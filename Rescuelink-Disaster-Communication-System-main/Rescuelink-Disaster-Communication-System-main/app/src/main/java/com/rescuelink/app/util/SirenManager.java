package com.rescuelink.app.util;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * FEAT-SIREN-02: pulsing SOS siren on the ALARM audio stream.
 *
 * Design constraints:
 *  - Uses STREAM_ALARM so it sounds even when the ringer is silent/vibrate.
 *  - Pulses (not continuous) to conserve battery; the gap lengthens on low battery,
 *    re-checked live on every pulse.
 *  - Hard max-duration cap so it can never run forever and drain the battery.
 *  - stop() fully releases the ToneGenerator (no audio-resource leak).
 *
 * This class NEVER touches the mesh; callers must broadcast first, then start the siren.
 */
public class SirenManager {

    private static final String TAG = "SirenManager";

    public interface Listener {
        /** Called (on the main thread) when the siren stops, whether by user or the cap. */
        void onSirenStopped();
    }

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGenerator;
    private boolean active = false;
    private long startedAt = 0L;
    private Listener listener;

    public SirenManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isActive() {
        return active;
    }

    /** When the siren will auto-stop (epoch millis), or 0 if not active. */
    public long getAutoStopAt() {
        return active ? startedAt + Constants.SIREN_MAX_DURATION_MS : 0L;
    }

    /** Begin pulsing. Safe to call again while active (no-op). Fires a haptic on the first pulse. */
    public void startPulsing() {
        if (active) return;
        try {
            // 100 = max ToneGenerator volume (0-100).
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (RuntimeException e) {
            // Some devices throw if the alarm stream is unavailable; fail quietly.
            Log.e(TAG, "ToneGenerator init failed: " + e.getMessage());
            toneGenerator = null;
        }
        active = true;
        startedAt = System.currentTimeMillis();
        vibrateOnce();
        pulse(); // first pulse immediately
    }

    private void pulse() {
        if (!active) return;

        // Hard cap: stop after the max duration.
        if (System.currentTimeMillis() - startedAt >= Constants.SIREN_MAX_DURATION_MS) {
            stop();
            return;
        }

        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L,
                        (int) Constants.SIREN_PULSE_DURATION_MS);
            } catch (RuntimeException e) {
                Log.e(TAG, "startTone failed: " + e.getMessage());
            }
        }

        // Re-check battery each pulse so the interval adapts live.
        long interval = DeviceUtils.isLowBattery(appContext)
                ? Constants.SIREN_INTERVAL_LOW_BATTERY_MS
                : Constants.SIREN_INTERVAL_NORMAL_MS;
        handler.postDelayed(this::pulse, interval);
    }

    private void vibrateOnce() {
        Vibrator vibrator = getVibrator();
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 300, 150, 300, 150, 300}, -1));
        }
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Stop pulsing and release audio resources. Idempotent. */
    public void stop() {
        boolean wasActive = active;
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
            } catch (RuntimeException ignored) {}
            toneGenerator.release();
            toneGenerator = null;
        }
        if (wasActive && listener != null) {
            listener.onSirenStopped();
        }
    }
}
