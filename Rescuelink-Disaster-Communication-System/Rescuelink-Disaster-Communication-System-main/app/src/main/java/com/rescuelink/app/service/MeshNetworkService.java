package com.rescuelink.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import com.rescuelink.app.R;
import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.entity.AlertEntity;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.ui.HomeActivity;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;
import com.rescuelink.app.util.MessageSerializer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core mesh networking service using Google Nearby Connections API.
 * Runs as a foreground service for continuous device discovery and message relay.
 *
 * MESH LOGIC:
 * 1. Every message has a UUID. The service maintains a "seen set" of UUIDs.
 * 2. On receiving a message: if UUID is in seen-set → DROP (duplicate).
 *    Otherwise → STORE in Room DB + RELAY to all connected peers.
 * 3. Messages include a TTL (time-to-live) decremented on each hop. TTL=0 → stop forwarding.
 * 4. SOS alerts get higher TTL (20 vs 10 for chat) for wider propagation.
 */
public class MeshNetworkService extends Service {

    private static final String TAG = "MeshNetworkService";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    // TASK-06: intent action used by MessageRelayWorker to trigger a re-broadcast.
    public static final String ACTION_REBROADCAST_PENDING = "com.rescuelink.app.action.REBROADCAST_PENDING";
    // FEAT-09: re-evaluate battery/settings-driven discovery duty cycle.
    public static final String ACTION_APPLY_POWER_MODE = "com.rescuelink.app.action.APPLY_POWER_MODE";

    private final IBinder binder = new MeshBinder();

    // Connected endpoints: endpointId -> endpointName
    private final Map<String, String> connectedEndpoints = new ConcurrentHashMap<>();

    // TASK-05: endpoints for which a connection has been requested but not yet resolved.
    // Prevents duplicate symmetric requestConnection() calls that cause IO_ERROR churn.
    private final Set<String> pendingConnections = Collections.synchronizedSet(new java.util.HashSet<>());

    // TASK-08: endpointId -> advertised name, captured at onConnectionInitiated and
    // promoted into connectedEndpoints once the connection is confirmed.
    private final Map<String, String> pendingEndpointNames = new ConcurrentHashMap<>();

    // FEAT-02/FEAT-09: last-seen timestamp and last self-reported battery per peer.
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    private final Map<String, Integer> peerBattery = new ConcurrentHashMap<>();

    // FEAT-SIREN-02: opt-in siren for incoming SOS (self-caps; default off).
    private com.rescuelink.app.util.SirenManager incomingSirenManager;

    // BRIDGE-CORE: opportunistic internet bridge (optional, offline-safe).
    private com.rescuelink.app.bridge.ConnectivityBridgeManager bridgeManager;

    // Seen message IDs to avoid duplicate processing (LRU cache)
    private final Set<String> seenMessageIds = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > Constants.MAX_SEEN_MESSAGES;
                }
            }));

    // LiveData for UI observation
    private final MutableLiveData<Integer> connectedDeviceCount = new MutableLiveData<>(0);
    private final MutableLiveData<MessageEntity> incomingMessage = new MutableLiveData<>();
    // TWO-WAY: responder status update addressed to THIS device (for the victim banner).
    private final MutableLiveData<MessageEntity> responderUpdate = new MutableLiveData<>();

    private AppDatabase database;
    private ExecutorService executor;
    private String deviceId;
    private String userName;
    private boolean isRunning = false;

    // ========== Binder ==========

    public class MeshBinder extends Binder {
        public MeshNetworkService getService() {
            return MeshNetworkService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ========== Lifecycle ==========

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getInstance(this);
        executor = Executors.newFixedThreadPool(4);
        deviceId = DeviceUtils.getDeviceId(this);
        userName = DeviceUtils.getUserName(this);
        createSosNotificationChannel();

        // SH-02: publish live status to the app-scoped holder for banners on every screen.
        MeshStatus.setPeersSupplier(this::getConnectedPeers);
        MeshStatus.setConnectedCount(0);

        // BRIDGE-CORE: start the opportunistic internet bridge. Never blocks the mesh; if
        // internet never arrives it simply idles. A successful upload is surfaced as a toast.
        bridgeManager = new com.rescuelink.app.bridge.ConnectivityBridgeManager(this);
        bridgeManager.setListener(count -> new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> android.widget.Toast.makeText(this,
                        getString(R.string.bridge_uploaded, count),
                        android.widget.Toast.LENGTH_SHORT).show()));
        // TWO-WAY: inject responder status updates pulled from the backend into the mesh.
        bridgeManager.setUpdateInjector(this::injectResponderUpdates);
        bridgeManager.start();

        // TASK-07: Seed the seen-set from the DB so previously stored messages are
        // not re-processed and re-broadcast after a service/process restart.
        executor.execute(() -> {
            List<String> recentIds = database.messageDao().getRecentMessageIds(Constants.MAX_SEEN_MESSAGES / 2);
            seenMessageIds.addAll(recentIds);
            Log.d(TAG, "Seeded seen-set with " + recentIds.size() + " stored message IDs");
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TASK-02: Promote to foreground immediately and unconditionally, before any
        // other work, so Android 12+ never throws ForegroundServiceDidNotStartInTime.
        // This is also safe on a START_STICKY redelivery where intent may be null.
        startForeground(Constants.NOTIFICATION_ID, buildNotification());

        // Only advertising/discovery are gated behind the running flag; a null intent
        // (sticky restart) must still bring the mesh back up.
        if (!isRunning) {
            startAdvertising();
            startDiscovery();
            isRunning = true;
            Log.d(TAG, "Mesh network service started. Device ID: " + deviceId);
        }

        // TASK-06: the background relay worker signals a re-broadcast of pending messages.
        if (intent != null && ACTION_REBROADCAST_PENDING.equals(intent.getAction())) {
            rebroadcastPendingMessages();
        }
        // FEAT-09: battery-saver toggle or battery change asks us to re-evaluate.
        if (intent != null && ACTION_APPLY_POWER_MODE.equals(intent.getAction()) && isRunning) {
            applyPowerMode();
        }
        return START_STICKY;
    }

    /**
     * TASK-06: re-send every pending (isRelayed=0) message to all currently connected
     * peers. Triggered by MessageRelayWorker so store-carry-forward keeps working even
     * when no new connection event fires.
     */
    private void rebroadcastPendingMessages() {
        for (String endpointId : connectedEndpoints.keySet()) {
            shareExistingMessages(endpointId);
        }
        Log.d(TAG, "Re-broadcast pending messages to " + connectedEndpoints.size() + " peer(s)");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dutyHandler.removeCallbacksAndMessages(null); // FEAT-09: stop duty-cycle timers
        MeshStatus.setConnectedCount(-1); // SH-02: mesh off
        MeshStatus.setPeersSupplier(null);
        if (bridgeManager != null) bridgeManager.stop(); // BRIDGE-CORE: unregister callback
        if (incomingSirenManager != null) incomingSirenManager.stop(); // FEAT-SIREN-02: no leak
        Nearby.getConnectionsClient(this).stopAdvertising();
        Nearby.getConnectionsClient(this).stopDiscovery();
        Nearby.getConnectionsClient(this).stopAllEndpoints();
        isRunning = false;
        executor.shutdown();
        Log.d(TAG, "Mesh network service stopped.");
    }

    // ========== Notification ==========

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // FEAT-09: surface low-power mode in the ongoing notification.
        String text = lowPowerMode
                ? getString(R.string.mesh_service_text_low_power)
                : getString(R.string.mesh_service_text);

        return new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.mesh_service_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    /**
     * TASK-09: high-importance channel so incoming SOS alerts produce a heads-up
     * notification with sound and vibration even when the app is backgrounded.
     */
    private void createSosNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                Constants.SOS_CHANNEL_ID,
                getString(R.string.sos_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.sos_channel_desc));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 250, 500});
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * TASK-09: raise a heads-up notification and vibrate on an incoming SOS.
     */
    private void notifyIncomingSos(MessageEntity message) {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String type = message.getEmergencyType() != null ? message.getEmergencyType() : "";
        String title = getString(R.string.sos_incoming_title);
        String text = getString(R.string.sos_incoming_text, message.getSenderName(), type);

        Notification notification = new NotificationCompat.Builder(this, Constants.SOS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 250, 500})
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Unique-ish id per SOS so multiple alerts stack instead of replacing.
            manager.notify(Constants.SOS_NOTIFICATION_BASE_ID + (message.getId().hashCode() & 0xFFF), notification);
        }

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 250, 500}, -1));
        }

        // FEAT-SIREN-02: incoming-SOS siren is OFF by default (a loud alarm could expose a
        // hiding user). Notification + vibration above always fire regardless. When opted in,
        // the siren self-caps at SIREN_MAX_DURATION_MS; stopIncomingSiren() gives one-tap silence.
        boolean incomingSiren = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_SIREN_ENABLED_INCOMING, false);
        if (incomingSiren) {
            if (incomingSirenManager == null) {
                incomingSirenManager = new com.rescuelink.app.util.SirenManager(this);
            }
            incomingSirenManager.startPulsing();
        }
    }

    /** FEAT-SIREN-02: stop any incoming-SOS siren (used by the notification Silence action). */
    public void stopIncomingSiren() {
        if (incomingSirenManager != null) incomingSirenManager.stop();
    }

    /** TWO-WAY: notify the victim that a responder update hopped back to them. */
    private void notifyResponderUpdate(MessageEntity message) {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, Constants.SOS_CHANNEL_ID)
                .setContentTitle(getString(R.string.responder_update_title))
                .setContentText(message.getContent())
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) {
            mgr.notify(Constants.SOS_NOTIFICATION_BASE_ID + 1, n);
        }
    }

    // ========== Nearby Connections: Advertising ==========

    // Endpoint name is advertised as "deviceId|userName" so the tie-breaker (TASK-05)
    // can compare this device's deviceId against the peer's deviceId symmetrically.
    private static final String NAME_SEP = "|";

    private String advertisedName() {
        return deviceId + NAME_SEP + userName;
    }

    private static String parseDeviceId(String endpointName) {
        if (endpointName == null) return "";
        int idx = endpointName.indexOf(NAME_SEP);
        return idx > 0 ? endpointName.substring(0, idx) : endpointName;
    }

    private static String parseUserName(String endpointName) {
        if (endpointName == null) return "";
        int idx = endpointName.indexOf(NAME_SEP);
        return idx >= 0 ? endpointName.substring(idx + 1) : endpointName;
    }

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        Nearby.getConnectionsClient(this)
                .startAdvertising(advertisedName(), Constants.SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> Log.d(TAG, "Advertising started"))
                .addOnFailureListener(e -> Log.e(TAG, "Advertising failed: " + e.getMessage()));
    }

    // ========== Nearby Connections: Discovery ==========

    // FEAT-09: battery-aware discovery duty cycle. Continuous scanning is the biggest
    // drain, so in low-power mode we scan for a short window then pause, repeating.
    // Advertising stays on the whole time, so the device is still reachable and can
    // always SEND an SOS regardless of battery mode.
    private final android.os.Handler dutyHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean lowPowerMode = false;
    private boolean discoveryActive = false;

    private void startDiscovery() {
        applyPowerMode(); // decide continuous vs duty-cycled based on battery/settings
    }

    /** Re-evaluate battery/settings and switch discovery between continuous and duty-cycled. */
    void applyPowerMode() {
        boolean saver = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_BATTERY_SAVER, false);
        boolean low = DeviceUtils.isLowBattery(this);
        lowPowerMode = saver || low;

        dutyHandler.removeCallbacksAndMessages(null);
        beginDiscoveryWindow();
        // Update the ongoing notification to surface the low-power indicator.
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Constants.NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    private void beginDiscoveryWindow() {
        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        Nearby.getConnectionsClient(this)
                .startDiscovery(Constants.SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> {
                    discoveryActive = true;
                    Log.d(TAG, "Discovery started (lowPower=" + lowPowerMode + ")");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Discovery failed: " + e.getMessage()));

        if (lowPowerMode) {
            // Scan for SCAN_WINDOW, then pause for SCAN_PAUSE, then repeat.
            dutyHandler.postDelayed(() -> {
                Nearby.getConnectionsClient(this).stopDiscovery();
                discoveryActive = false;
                dutyHandler.postDelayed(this::beginDiscoveryWindow, Constants.LOW_POWER_SCAN_PAUSE_MS);
            }, Constants.LOW_POWER_SCAN_WINDOW_MS);
        }
    }

    public boolean isLowPowerMode() {
        return lowPowerMode;
    }

    // ========== Endpoint Discovery Callback ==========

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            Log.d(TAG, "Endpoint found: " + endpointId + " (" + info.getEndpointName() + ")");

            // TASK-05: Ignore endpoints already connected or with an in-flight request.
            if (connectedEndpoints.containsKey(endpointId) || pendingConnections.contains(endpointId)) {
                Log.d(TAG, "Skipping already-connected/in-flight endpoint: " + endpointId);
                return;
            }

            // TASK-05: Deterministic tie-breaker. Both devices discover each other and
            // would otherwise each requestConnection(), creating duplicate symmetric links.
            // The peer's stable deviceId travels in its advertised endpoint name; only the
            // device with the lexicographically greater deviceId initiates. The other waits
            // to be connected to, so exactly one side requests the connection.
            String peerDeviceId = parseDeviceId(info.getEndpointName());
            if (deviceId.compareTo(peerDeviceId) <= 0) {
                Log.d(TAG, "Deferring connect to " + endpointId + "; waiting to be connected to");
                return;
            }

            pendingConnections.add(endpointId);
            Nearby.getConnectionsClient(MeshNetworkService.this)
                    .requestConnection(advertisedName(), endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Connection requested to: " + endpointId))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Connection request failed: " + e.getMessage());
                        pendingConnections.remove(endpointId);
                    });
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Endpoint lost: " + endpointId);
            pendingConnections.remove(endpointId);
        }
    };

    // ========== Connection Lifecycle Callback ==========

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            Log.d(TAG, "Connection initiated with: " + info.getEndpointName());
            // TASK-08: capture the human-readable peer name (the userName portion of the
            // "deviceId|userName" advertised name) so it is available once confirmed.
            pendingEndpointNames.put(endpointId, parseUserName(info.getEndpointName()));
            // Auto-accept all connections (mesh network strategy)
            Nearby.getConnectionsClient(MeshNetworkService.this)
                    .acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            pendingConnections.remove(endpointId);
            if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Connected to: " + endpointId);
                // TASK-08: store endpointId -> peer name (fall back to id if unknown).
                String name = pendingEndpointNames.remove(endpointId);
                connectedEndpoints.put(endpointId, name != null ? name : endpointId);
                updateDeviceCount();

                // Share any pending (unrelayed) messages with the newly connected peer.
                shareExistingMessages(endpointId);
            } else {
                pendingEndpointNames.remove(endpointId);
                Log.d(TAG, "Connection failed with: " + endpointId +
                        " Status: " + result.getStatus().getStatusCode());
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected from: " + endpointId);
            connectedEndpoints.remove(endpointId);
            pendingConnections.remove(endpointId);
            pendingEndpointNames.remove(endpointId);
            peerLastSeen.remove(endpointId);
            peerBattery.remove(endpointId);
            updateDeviceCount();
        }
    };

    // ========== Payload Callback (Message Receiving) ==========

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES && payload.asBytes() != null) {
                MessageEntity message = MessageSerializer.deserialize(payload.asBytes());
                if (message != null) {
                    // FEAT-02/FEAT-09: refresh last-seen and battery for this peer.
                    peerLastSeen.put(endpointId, System.currentTimeMillis());
                    if (message.getBatteryLevel() > 0) {
                        peerBattery.put(endpointId, message.getBatteryLevel());
                    }
                    processIncomingMessage(message, endpointId);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // Not needed for byte payloads (they complete instantly)
        }
    };

    // ========== Mesh Logic: Process & Relay ==========

    /**
     * Core mesh networking logic:
     * 1. Check if message UUID is in seen-set → drop if duplicate
     * 2. Add UUID to seen-set
     * 3. Store message in local Room database
     * 4. If message is SOS, also store as alert
     * 5. Decrement TTL and relay to all connected peers (if TTL > 0)
     */
    private void processIncomingMessage(MessageEntity message, String senderEndpointId) {
        String messageId = message.getId();

        // Step 1: Duplicate check
        if (seenMessageIds.contains(messageId)) {
            Log.d(TAG, "Duplicate message dropped: " + messageId);
            return;
        }

        // Step 2: Add to seen set (size cap handled by LinkedHashMap)
        seenMessageIds.add(messageId);

        // Step 3: Store in database. This object is inserted with isRelayed as received
        // (0) and is NEVER mutated by the relay path below (TASK-03/TASK-04).
        message.setMine(false); // It's from someone else
        message.setRelayed(false);
        executor.execute(() -> {
            database.messageDao().insert(message);

            // Step 4: If SOS, also save as an ACTIVE alert (FEAT-05/06 detail fields).
            if (message.isSOSAlert()) {
                AlertEntity alert = new AlertEntity();
                alert.setId(message.getId());
                alert.setUserName(message.getSenderName());
                alert.setLatitude(message.getLatitude());
                alert.setLongitude(message.getLongitude());
                alert.setEmergencyType(message.getEmergencyType());
                alert.setTimestamp(message.getTimestamp());
                alert.setSenderId(message.getSenderId());
                alert.setStatus(Constants.ALERT_ACTIVE);
                alert.setMedicalNote(message.getMedicalNote());
                alert.setBloodGroup(message.getBloodGroup());
                alert.setBatteryLevel(message.getBatteryLevel());
                alert.setHopCount(message.getHopCount());
                database.alertDao().insert(alert);
            }

            // FEAT-04/FEAT-06: apply lifecycle updates to the referenced alert.
            applyLifecycle(message);
        });

        // Notify UI
        incomingMessage.postValue(message);

        // TASK-09: alert a backgrounded user to an incoming SOS.
        if (message.isSOSAlert()) {
            notifyIncomingSos(message);
        }

        // TWO-WAY: a responder update addressed to THIS device — reassure the victim.
        if (Constants.KIND_RESPONDER_UPDATE.equals(message.getKind())
                && deviceId.equals(message.getRefId())) {
            responderUpdate.postValue(message);
            notifyResponderUpdate(message);
        }

        // Step 5: Relay to other peers (mesh hop).
        // TASK-04: operate on a defensive copy so the stored object's TTL stays stable.
        // Only relay when the decremented TTL is still > 0.
        MessageEntity relayCopy = message.copy();
        relayCopy.setTtl(message.getTtl() - 1);
        relayCopy.setHopCount(message.getHopCount() + 1); // FEAT-03: count this hop
        if (relayCopy.getTtl() > 0) {
            relayMessage(relayCopy, senderEndpointId, message.getId()); // exclude sender
        }
    }

    /**
     * FEAT-04/FEAT-06: apply a lifecycle message to stored alerts. Must run on the executor.
     * CANCEL / SAFE resolve the referenced alert (and any active alert from the same sender);
     * ACK marks the referenced alert acknowledged.
     */
    private void applyLifecycle(MessageEntity message) {
        String kind = message.getKind();
        if (kind == null) return;
        switch (kind) {
            case Constants.KIND_CANCEL:
            case Constants.KIND_SAFE:
                if (message.getRefId() != null) {
                    database.alertDao().resolveById(message.getRefId());
                }
                if (message.getSenderId() != null) {
                    database.alertDao().resolveBySender(message.getSenderId());
                }
                break;
            case Constants.KIND_ACK:
                if (message.getRefId() != null) {
                    database.alertDao().setStatus(message.getRefId(), Constants.ALERT_ACKNOWLEDGED);
                }
                break;
            default:
                // SOS / CHAT: nothing to do here
                break;
        }
    }

    /**
     * TWO-WAY: turn backend responder updates into RESPONDER_UPDATE mesh messages so they
     * hop back to victims who are still offline. Each carries a fresh UUID (normal TTL +
     * dedup); refId = target victim senderId, content = human-readable status.
     */
    public void injectResponderUpdates(java.util.List<com.rescuelink.app.bridge.BackendClient.Update> updates) {
        for (com.rescuelink.app.bridge.BackendClient.Update u : updates) {
            if (u.senderId == null || u.status == null) continue;
            MessageEntity m = new MessageEntity();
            m.setId(DeviceUtils.generateMessageId());
            m.setSenderId(deviceId);          // this bridge device is the origin of the update
            m.setSenderName(userName);
            m.setRefId(u.senderId);           // addressed to the original victim
            m.setKind(Constants.KIND_RESPONDER_UPDATE);
            m.setEmergencyType(u.status);     // raw status (ACKNOWLEDGED / EN_ROUTE / RESOLVED)
            m.setContent(humanStatus(u.status));
            m.setTimestamp(System.currentTimeMillis());
            m.setTtl(Constants.SOS_TTL);
            m.setSOSAlert(false);
            m.setMine(true);
            m.setHopCount(0);
            sendMessage(m);
        }
    }

    private String humanStatus(String status) {
        switch (status) {
            case "ACKNOWLEDGED": return "A responder has seen your SOS.";
            case "EN_ROUTE": return "A responder has seen your SOS and is on the way.";
            case "RESOLVED": return "Your SOS has been marked resolved by responders.";
            default: return "Responder update: " + status;
        }
    }

    // ========== Sending Messages ==========

    /**
     * Send a new message from this device to all connected peers.
     */
    public void sendMessage(MessageEntity message) {
        // Mark as seen so we don't process our own message
        seenMessageIds.add(message.getId());
        message.setMine(true);
        // TASK-01/TASK-03: own messages start unrelayed. They are marked relayed only
        // after a successful broadcast, and remain isRelayed=0 (eligible for
        // store-carry-forward) if there are no peers at send time.
        message.setRelayed(false);

        // Store locally (this object is not mutated by the relay path)
        executor.execute(() -> {
            database.messageDao().insert(message);

            if (message.isSOSAlert()) {
                AlertEntity alert = new AlertEntity();
                alert.setId(message.getId());
                alert.setUserName(message.getSenderName());
                alert.setLatitude(message.getLatitude());
                alert.setLongitude(message.getLongitude());
                alert.setEmergencyType(message.getEmergencyType());
                alert.setTimestamp(message.getTimestamp());
                alert.setSenderId(message.getSenderId());
                alert.setStatus(Constants.ALERT_ACTIVE);
                alert.setMedicalNote(message.getMedicalNote());
                alert.setBloodGroup(message.getBloodGroup());
                alert.setBatteryLevel(message.getBatteryLevel());
                alert.setHopCount(0);
                database.alertDao().insert(alert);
            }

            // FEAT-04/FEAT-06: our own CANCEL / SAFE / ACK also update local alerts.
            applyLifecycle(message);
        });

        // Broadcast to all connected peers on a defensive copy. If zero peers are
        // connected, relayMessage sends nothing and isRelayed stays 0.
        relayMessage(message.copy(), null, message.getId());
    }

    /**
     * Relay a message to all connected peers (except the one who sent it).
     *
     * @param message    The message (copy) to relay
     * @param excludeId  Endpoint ID to exclude (null = send to all)
     * @param dbId       The stored message ID to mark relayed once at least one send
     *                   succeeds (TASK-03). Pass null to skip persistence of relay state.
     */
    private void relayMessage(MessageEntity message, String excludeId, String dbId) {
        byte[] data = MessageSerializer.serialize(message);
        Payload payload = Payload.fromBytes(data);

        // TASK-03: mark relayed only after the first successful send to a peer.
        final boolean[] markedRelayed = {false};

        for (String endpointId : connectedEndpoints.keySet()) {
            if (excludeId != null && excludeId.equals(endpointId)) {
                continue;
            }
            Nearby.getConnectionsClient(this)
                    .sendPayload(endpointId, payload)
                    .addOnSuccessListener(unused -> {
                        Log.d(TAG, "Message relayed to: " + endpointId);
                        if (dbId != null) {
                            synchronized (markedRelayed) {
                                if (!markedRelayed[0]) {
                                    markedRelayed[0] = true;
                                    executor.execute(() -> database.messageDao().markAsRelayed(dbId));
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Relay failed to " + endpointId + ": " + e.getMessage()));
        }
    }

    /**
     * Share pending (unrelayed) messages with a newly connected peer. This is the
     * store-carry-forward delivery path (TASK-01): it forwards every message not yet
     * relayed, including this device's own SOS (isMine=1). On success the message is
     * marked relayed (TASK-03).
     */
    private void shareExistingMessages(String endpointId) {
        executor.execute(() -> {
            List<MessageEntity> messages = database.messageDao().getPendingRelayMessages();
            for (MessageEntity msg : messages) {
                byte[] data = MessageSerializer.serialize(msg);
                Payload payload = Payload.fromBytes(data);
                final String msgId = msg.getId();
                Nearby.getConnectionsClient(this)
                        .sendPayload(endpointId, payload)
                        .addOnSuccessListener(unused -> {
                            Log.d(TAG, "Shared pending message " + msgId + " to " + endpointId);
                            executor.execute(() -> database.messageDao().markAsRelayed(msgId));
                        })
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Share failed for " + msgId + ": " + e.getMessage()));
            }
        });
    }

    // ========== LiveData Accessors ==========

    private void updateDeviceCount() {
        int count = connectedEndpoints.size();
        connectedDeviceCount.postValue(count);
        MeshStatus.setConnectedCount(count); // SH-02: publish to the app-scoped holder
    }

    public LiveData<Integer> getConnectedDeviceCount() {
        return connectedDeviceCount;
    }

    public LiveData<MessageEntity> getIncomingMessage() {
        return incomingMessage;
    }

    /** TWO-WAY: observe responder updates addressed to this device. */
    public LiveData<MessageEntity> getResponderUpdate() {
        return responderUpdate;
    }

    public int getConnectedCount() {
        return connectedEndpoints.size();
    }

    /**
     * TASK-08: connected peer display names for the UI (e.g. a nearby-devices sheet).
     */
    public List<String> getConnectedPeerNames() {
        return new java.util.ArrayList<>(connectedEndpoints.values());
    }

    /**
     * FEAT-02/FEAT-09: snapshot of connected peers with name, last-reported battery,
     * and last-seen time for the nearby-devices sheet.
     */
    public List<PeerInfo> getConnectedPeers() {
        List<PeerInfo> peers = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : connectedEndpoints.entrySet()) {
            String id = e.getKey();
            Integer battery = peerBattery.get(id);
            Long seen = peerLastSeen.get(id);
            peers.add(new PeerInfo(id, e.getValue(),
                    battery != null ? battery : -1,
                    seen != null ? seen : 0L));
        }
        return peers;
    }
}
