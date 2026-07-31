package com.rescuelink.app.util;

import android.content.Context;
import android.os.BatteryManager;

import java.util.UUID;

public class DeviceUtils {

    /**
     * Get a unique device ID (persisted via SharedPreferences).
     */
    public static String getDeviceId(Context context) {
        String deviceId = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_DEVICE_ID, null);

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().substring(0, 8);
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(Constants.PREF_DEVICE_ID, deviceId)
                    .apply();
        }
        return deviceId;
    }

    /**
     * Get or set the user display name.
     */
    public static String getUserName(Context context) {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_USER_NAME, "User-" + getDeviceId(context));
    }

    public static void setUserName(Context context, String name) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.PREF_USER_NAME, name)
                .apply();
    }

    /**
     * Get current battery level as percentage.
     */
    public static int getBatteryLevel(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return -1;
    }

    /**
     * Check if battery is low (below threshold for emergency mode).
     */
    public static boolean isLowBattery(Context context) {
        return getBatteryLevel(context) <= Constants.LOW_BATTERY_THRESHOLD;
    }

    /**
     * Generate a unique message ID.
     */
    public static String generateMessageId() {
        return UUID.randomUUID().toString();
    }
}
