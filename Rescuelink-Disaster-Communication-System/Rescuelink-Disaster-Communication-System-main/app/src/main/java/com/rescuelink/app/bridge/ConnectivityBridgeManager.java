package com.rescuelink.app.bridge;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.worker.MessageRelayWorker;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BRIDGE-CORE: the opportunistic internet bridge — RescueLink's differentiator.
 *
 * Any device can bridge; there is no special role. Whoever gets signal first carries
 * everyone's queued SOS out. This is 100% optional and offline-safe:
 *   - It only ever RUNS when the OS reports validated internet.
 *   - All work is on a background executor; nothing here touches the mesh critical path.
 *   - Failures are swallowed and left for WorkManager to retry with backoff.
 * If the internet never comes, the mesh behaves exactly as before this class existed.
 */
public class ConnectivityBridgeManager {

    private static final String TAG = "BridgeManager";
    public static final String WORK_NAME = "sos_bridge_upload";

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BackendClient client = new BackendClient();
    private ConnectivityManager.NetworkCallback callback;

    public interface BridgeListener {
        /** Called on a background thread after N alerts were uploaded (N >= 1). */
        void onBridged(int count);
    }

    /** TWO-WAY: hands fetched responder updates to the mesh service for re-injection. */
    public interface UpdateInjector {
        void injectResponderUpdates(List<BackendClient.Update> updates);
    }

    private volatile BridgeListener listener;
    private volatile UpdateInjector injector;

    public ConnectivityBridgeManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(BridgeListener listener) {
        this.listener = listener;
    }

    public void setUpdateInjector(UpdateInjector injector) {
        this.injector = injector;
    }

    /** Start listening for internet. Safe to call once (e.g. from the mesh service onCreate). */
    public void start() {
        ConnectivityManager cm =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || callback != null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();

        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Internet gained — attempting opportunistic SOS bridge");
                // Try immediately, and also enqueue a WorkManager job so retries survive
                // process death / transient failures.
                syncNow();
                enqueueRetry(appContext);
            }
        };
        try {
            cm.registerNetworkCallback(request, callback);
        } catch (RuntimeException e) {
            // Some OEMs throw TooManyRequests; degrade to WorkManager-only.
            Log.e(TAG, "registerNetworkCallback failed: " + e.getMessage());
            callback = null;
        }
    }

    public void stop() {
        ConnectivityManager cm =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && callback != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {}
            callback = null;
        }
    }

    /** Upload unsynced SOS now, on the background executor. Never throws to the caller. */
    public void syncNow() {
        executor.execute(this::uploadUnsyncedBlocking);
    }

    /**
     * Synchronous upload used by both syncNow() and the WorkManager worker.
     * Returns true if there is nothing left to sync (success or empty), false to retry.
     */
    public boolean uploadUnsyncedBlocking() {
        try {
            AppDatabase db = AppDatabase.getInstance(appContext);
            List<MessageEntity> pending = db.messageDao().getUnsyncedSosMessages();

            if (!pending.isEmpty()) {
                boolean ok = client.ingest(pending);
                if (!ok) return false; // let WorkManager retry
                for (MessageEntity m : pending) {
                    db.messageDao().markSynced(m.getId());
                }
                BridgeListener l = listener;
                if (l != null) l.onBridged(pending.size());
                Log.d(TAG, "Bridged " + pending.size() + " SOS alert(s) to backend");
            }

            // TWO-WAY: while online, pull responder status updates and inject them back
            // into the mesh so they hop to victims still offline. Best-effort.
            pullAndInjectUpdates();
            return true;
        } catch (IOException e) {
            Log.d(TAG, "Bridge upload failed (offline?), will retry: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Absolutely never crash the app for a bridge failure.
            Log.e(TAG, "Bridge upload error: " + e.getMessage());
            return false;
        }
    }

    /** TWO-WAY: pull responder updates since the last seen timestamp and inject them. */
    private void pullAndInjectUpdates() {
        UpdateInjector inj = injector;
        if (inj == null) return; // service not up; nothing to inject into
        try {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                    com.rescuelink.app.util.Constants.PREFS_NAME, Context.MODE_PRIVATE);
            long since = prefs.getLong(com.rescuelink.app.util.Constants.PREF_LAST_UPDATE_TS, 0L);
            BackendClient.UpdatesResponse resp = client.fetchUpdates(since);
            if (resp == null || resp.updates == null || resp.updates.isEmpty()) return;
            inj.injectResponderUpdates(resp.updates);
            prefs.edit().putLong(com.rescuelink.app.util.Constants.PREF_LAST_UPDATE_TS,
                    resp.serverTime).apply();
            Log.d(TAG, "Injected " + resp.updates.size() + " responder update(s) into mesh");
        } catch (Exception e) {
            Log.d(TAG, "pullUpdates failed (offline?), will retry: " + e.getMessage());
        }
    }

    /** Enqueue a WorkManager retry (network-constrained, exponential backoff). */
    public static void enqueueRetry(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(MessageRelayWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, work);
    }
}
