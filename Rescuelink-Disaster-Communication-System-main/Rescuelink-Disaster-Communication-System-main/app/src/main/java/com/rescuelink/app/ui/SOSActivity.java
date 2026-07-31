package com.rescuelink.app.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.ChipGroup;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.service.LocationService;
import com.rescuelink.app.service.MeshNetworkService;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;
import com.rescuelink.app.viewmodel.SOSViewModel;

import java.util.Locale;

public class SOSActivity extends AppCompatActivity {

    private SOSViewModel viewModel;
    private LocationService locationService;
    private MeshNetworkService meshService;
    private boolean isBound = false;

    private TextView tvLocation;
    private TextView tvBatteryLevel;
    private EditText etUserName;
    private EditText etEmergencyDetails;    
    private ChipGroup chipGroupEmergency;
    private Button btnSOS;
    private View pulseView;
    private com.google.android.material.button.MaterialButton btnImSafe;
    private com.google.android.material.button.MaterialButton btnResend;

    // FEAT-04: hold-to-confirm state
    private static final long HOLD_DURATION_MS = 3000;
    private final android.os.Handler holdHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable holdRunnable;
    private boolean holdFired = false;

    // FEAT-04: id of the most recent SOS this device sent (for cancel / resend)
    private MessageEntity lastSosMessage;

    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private ObjectAnimator pulseAnimator;

    // FEAT-01: cached profile so its medical note / blood group ride along with SOS.
    private String profileBloodGroup = "";
    private String profileMedicalNote = "";

    // FEAT-SIREN-02: audible siren + its banner UI.
    private com.rescuelink.app.util.SirenManager sirenManager;
    private View sirenBanner;
    private TextView tvSirenStatus;
    private TextView tvSirenHint;
    private final android.os.Handler sirenUiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable sirenTicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);

        // Initialize views
        tvLocation = findViewById(R.id.tvLocation);
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel);
        etUserName = findViewById(R.id.etUserName);
        etEmergencyDetails = findViewById(R.id.etEmergencyDetails);
        chipGroupEmergency = findViewById(R.id.chipGroupEmergency);
        btnSOS = findViewById(R.id.btnSOS);
        pulseView = findViewById(R.id.pulseView);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(SOSViewModel.class);

        // FEAT-01: keep profile medical data ready to attach to an outgoing SOS.
        viewModel.getProfile().observe(this, profile -> {
            if (profile != null) {
                profileBloodGroup = profile.getBloodGroup() != null ? profile.getBloodGroup() : "";
                profileMedicalNote = profile.getMedicalNote() != null ? profile.getMedicalNote() : "";
            }
        });

        // Pre-fill user name
        etUserName.setText(DeviceUtils.getUserName(this));

        // Battery level
        int battery = DeviceUtils.getBatteryLevel(this);
        tvBatteryLevel.setText("🔋 Battery: " + battery + "%");

        if (DeviceUtils.isLowBattery(this)) {
            tvBatteryLevel.setTextColor(getColor(R.color.status_danger));
        }

        // Location
        locationService = new LocationService(this);
        locationService.startLocationUpdates();
        locationService.getCurrentLocation().observe(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                tvLocation.setText(String.format(Locale.US, "Lat: %.6f, Lng: %.6f", currentLat, currentLng));
            }
        });

        // Pulse animation for SOS button background
        startPulseAnimation();

        // FEAT-04: hold-to-confirm SOS to prevent accidental single-tap sends.
        setupHoldToSend();

        // FEAT-04: "I am safe" and "Resend" actions
        btnImSafe = findViewById(R.id.btnImSafe);
        btnResend = findViewById(R.id.btnResend);
        btnImSafe.setOnClickListener(v -> broadcastImSafe());
        btnResend.setOnClickListener(v -> resendLastSos());

        // SH-02: mesh status banner (holder-backed)
        View meshBanner = findViewById(R.id.meshStatusBanner);
        if (meshBanner != null) {
            new com.rescuelink.app.ui.widget.MeshStatusController(this, meshBanner).bindToHolder();
        }

        // FEAT-SIREN-02: siren + banner
        sirenManager = new com.rescuelink.app.util.SirenManager(this);
        sirenBanner = findViewById(R.id.sirenBanner);
        tvSirenStatus = findViewById(R.id.tvSirenStatus);
        tvSirenHint = findViewById(R.id.tvSirenHint);
        sirenManager.setListener(this::hideSirenBanner);
        View btnSilence = findViewById(R.id.btnSilence);
        btnSilence.setOnClickListener(v -> sirenManager.stop()); // listener hides banner

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MeshNetworkService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        locationService.stopLocationUpdates();
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (holdRunnable != null) holdHandler.removeCallbacks(holdRunnable);
        // FEAT-SIREN-02: leaving the screen stops the siren and releases audio (no leak).
        if (sirenManager != null) sirenManager.stop();
        hideSirenBanner();
    }

    private void sendSOS() {
        String userName = etUserName.getText().toString().trim();
        if (userName.isEmpty()) {
            userName = DeviceUtils.getUserName(this);
        } else {
            DeviceUtils.setUserName(this, userName);
        }

        // TASK-08: never broadcast an SOS with a silent 0.0,0.0 location. Try a
        // last-known fix first; if still unavailable, block the send and tell the user.
        if (currentLat == 0.0 && currentLng == 0.0) {
            Location lastKnown = locationService.getLastKnownLocation();
            if (lastKnown != null) {
                currentLat = lastKnown.getLatitude();
                currentLng = lastKnown.getLongitude();
                tvLocation.setText(String.format(Locale.US, "Lat: %.6f, Lng: %.6f", currentLat, currentLng));
            }
        }
        if (currentLat == 0.0 && currentLng == 0.0) {
            Toast.makeText(this, R.string.sos_location_unavailable_warning, Toast.LENGTH_LONG).show();
            return;
        }

        String details = etEmergencyDetails != null ? etEmergencyDetails.getText().toString().trim().toLowerCase() : "";

        // --- RULE-BASED KEYWORD TRIAGE (TASK-10: not AI/NLP) ---
        String emergencyType = Constants.EMERGENCY_OTHER;

        // If they wrote details, keyword matching classifies the emergency type.
        if (!details.isEmpty()) {
            if (details.contains("bleed") || details.contains("broken") || details.contains("hurt") || details.contains("pain") || details.contains("heart")) {
                emergencyType = Constants.EMERGENCY_MEDICAL;
                chipGroupEmergency.check(R.id.chipMedical);
            } else if (details.contains("fire") || details.contains("burn") || details.contains("smoke")) {
                emergencyType = Constants.EMERGENCY_FIRE;
                chipGroupEmergency.check(R.id.chipFire);
            } else if (details.contains("shake") || details.contains("rubble") || details.contains("collapse") || details.contains("earthquake")) {
                emergencyType = Constants.EMERGENCY_EARTHQUAKE;
                chipGroupEmergency.check(R.id.chipEarthquake);
            } else if (details.contains("water") || details.contains("flood") || details.contains("drown")) {
                emergencyType = Constants.EMERGENCY_FLOOD;
                chipGroupEmergency.check(R.id.chipFlood);
            } else {
                // No keyword matched; respect any manual chip selection.
                emergencyType = getSelectedEmergencyType();
            }
            Toast.makeText(this, getString(R.string.triage_applied, emergencyType), Toast.LENGTH_SHORT).show();
        } else {
            // Fallback to manual selection
            emergencyType = getSelectedEmergencyType();
        }

        int battery = DeviceUtils.getBatteryLevel(this);

        // Create SOS alert (FEAT-01: include profile medical note + blood group)
        MessageEntity sosMessage = viewModel.createSOSAlert(
                userName, emergencyType, currentLat, currentLng, battery,
                profileBloodGroup, profileMedicalNote);

        // Send via mesh service — the life-saving action, FIRST and unconditionally.
        lastSosMessage = sosMessage;
        if (isBound && meshService != null) {
            meshService.sendMessage(sosMessage);
        }

        // FEAT-SIREN-02: ONLY AFTER the mesh broadcast, start the siren (if opted in).
        // Nothing above this line depends on audio; the broadcast is never delayed.
        boolean sirenOn = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_SIREN_ENABLED_OUTGOING, true);
        if (sirenOn) {
            startSiren();
        } else {
            // Fallback single haptic when the siren is off, to confirm the send.
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        // Visual feedback - flash button
        btnSOS.setEnabled(false);
        btnSOS.setText("✓ SENT");
        btnSOS.postDelayed(() -> {
            btnSOS.setEnabled(true);
            btnSOS.setText(R.string.sos_button_text);
        }, 3000);
        btnResend.setEnabled(true);

        // FEAT-04: 10-second undo window. Cancelling broadcasts a CANCEL that retracts
        // the alert across the mesh (dedup-safe — it carries its own UUID).
        com.google.android.material.snackbar.Snackbar
                .make(btnSOS, R.string.sos_sent_success, (int) Constants.SOS_CANCEL_WINDOW_MS)
                .setAction(R.string.sos_undo, v -> cancelLastSos())
                .show();
    }

    /** FEAT-SIREN-02: show the banner and start the pulsing alarm + countdown ticker. */
    private void startSiren() {
        sirenManager.startPulsing();
        sirenBanner.setVisibility(View.VISIBLE);
        tvSirenStatus.setText(R.string.siren_active);
        startSirenTicker();
    }

    private void startSirenTicker() {
        if (sirenTicker != null) sirenUiHandler.removeCallbacks(sirenTicker);
        sirenTicker = new Runnable() {
            @Override
            public void run() {
                if (!sirenManager.isActive()) { hideSirenBanner(); return; }
                long remainingMs = sirenManager.getAutoStopAt() - System.currentTimeMillis();
                long secs = Math.max(0, remainingMs / 1000);
                String hint = getString(R.string.siren_auto_stop, secs);
                if (DeviceUtils.isLowBattery(SOSActivity.this)) {
                    hint = getString(R.string.siren_low_power) + " · " + hint;
                }
                tvSirenHint.setText(hint);
                sirenUiHandler.postDelayed(this, 1000);
            }
        };
        sirenUiHandler.post(sirenTicker);
    }

    /** FEAT-SIREN-02: hide the banner and stop the countdown (siren already stopped). */
    private void hideSirenBanner() {
        if (sirenTicker != null) sirenUiHandler.removeCallbacks(sirenTicker);
        if (sirenBanner != null) sirenBanner.setVisibility(View.GONE);
    }

    /** FEAT-04: broadcast a CANCEL for the most recent SOS. */
    private void cancelLastSos() {
        if (lastSosMessage == null) return;
        String userName = DeviceUtils.getUserName(this);
        MessageEntity cancel = viewModel.createLifecycleMessage(
                Constants.KIND_CANCEL, lastSosMessage.getId(), userName);
        if (isBound && meshService != null) {
            meshService.sendMessage(cancel);
        }
        Toast.makeText(this, R.string.sos_cancelled, Toast.LENGTH_SHORT).show();
    }

    /** FEAT-04: broadcast "I am safe now", resolving the user's prior SOS across the mesh. */
    private void broadcastImSafe() {
        String userName = DeviceUtils.getUserName(this);
        String ref = lastSosMessage != null ? lastSosMessage.getId() : null;
        MessageEntity safe = viewModel.createLifecycleMessage(Constants.KIND_SAFE, ref, userName);
        if (isBound && meshService != null) {
            meshService.sendMessage(safe);
        }
        Toast.makeText(this, R.string.sos_safe_broadcast, Toast.LENGTH_SHORT).show();
    }

    /** FEAT-04: re-broadcast the last SOS (e.g. after moving to a new area). */
    private void resendLastSos() {
        if (lastSosMessage == null) {
            Toast.makeText(this, R.string.sos_nothing_to_resend, Toast.LENGTH_SHORT).show();
            return;
        }
        // Fresh id + current location/battery so it propagates as a new alert.
        int battery = DeviceUtils.getBatteryLevel(this);
        MessageEntity resend = viewModel.createSOSAlert(
                lastSosMessage.getSenderName(), lastSosMessage.getEmergencyType(),
                currentLat, currentLng, battery, profileBloodGroup, profileMedicalNote);
        lastSosMessage = resend;
        if (isBound && meshService != null) {
            meshService.sendMessage(resend);
        }
        Toast.makeText(this, R.string.sos_resent, Toast.LENGTH_SHORT).show();
    }

    /**
     * FEAT-04: hold-to-confirm. The user must press and hold the SOS button for
     * {@link #HOLD_DURATION_MS} with a haptic ramp; a single tap does nothing.
     */
    @SuppressWarnings("ClickableViewAccessibility")
    private void setupHoldToSend() {
        btnSOS.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    holdFired = false;
                    btnSOS.setText(R.string.sos_holding);
                    rampHaptics();
                    holdRunnable = () -> {
                        holdFired = true;
                        btnSOS.setText(R.string.sos_button_text);
                        sendSOS();
                    };
                    holdHandler.postDelayed(holdRunnable, HOLD_DURATION_MS);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!holdFired) {
                        holdHandler.removeCallbacks(holdRunnable);
                        btnSOS.setText(R.string.sos_button_text);
                    }
                    v.performClick();
                    return true;
            }
            return false;
        });
    }

    /** Escalating haptic pulses during the hold, as tactile confirmation. */
    private void rampHaptics() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 40, 700, 60, 700, 90};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    private String getSelectedEmergencyType() {
        int checkedId = chipGroupEmergency.getCheckedChipId();
        if (checkedId == R.id.chipFlood) return Constants.EMERGENCY_FLOOD;
        if (checkedId == R.id.chipEarthquake) return Constants.EMERGENCY_EARTHQUAKE;
        if (checkedId == R.id.chipFire) return Constants.EMERGENCY_FIRE;
        if (checkedId == R.id.chipMedical) return Constants.EMERGENCY_MEDICAL;
        if (checkedId == R.id.chipCyclone) return Constants.EMERGENCY_CYCLONE;
        return Constants.EMERGENCY_OTHER;
    }

    private void startPulseAnimation() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 0f);

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseView, scaleX, scaleY, alpha);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ObjectAnimator.RESTART);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.start();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshNetworkService.MeshBinder binder = (MeshNetworkService.MeshBinder) service;
            meshService = binder.getService();
            isBound = true;

            // TWO-WAY: show a reassurance banner when a responder update hops back to us.
            meshService.getResponderUpdate().observe(SOSActivity.this, msg -> {
                if (msg == null) return;
                View banner = findViewById(R.id.responderBanner);
                if (banner instanceof TextView) {
                    ((TextView) banner).setText(msg.getContent());
                    banner.setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };
}
