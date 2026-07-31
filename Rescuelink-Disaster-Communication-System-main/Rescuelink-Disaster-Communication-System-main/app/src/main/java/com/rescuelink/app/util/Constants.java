package com.rescuelink.app.util;

public class Constants {

    // Nearby Connections
    public static final String SERVICE_ID = "com.rescuelink.app.mesh";
    public static final String DEVICE_ID_KEY = "device_id";
    public static final String USER_NAME_KEY = "user_name";

    // Mesh Networking
    public static final int DEFAULT_TTL = 10;
    public static final int SOS_TTL = 20;
    public static final int MAX_SEEN_MESSAGES = 1000;

    // Message Types
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_SOS = "SOS";

    // Message kinds carried in the payload (FEAT-04 / FEAT-06 lifecycle)
    public static final String KIND_SOS = "SOS";
    public static final String KIND_CHAT = "CHAT";
    public static final String KIND_CANCEL = "CANCEL"; // retract a prior SOS
    public static final String KIND_SAFE = "SAFE";     // "I am safe now"
    public static final String KIND_ACK = "ACK";       // a responder is responding
    // TWO-WAY: responder status hopped back to the victim through the mesh.
    public static final String KIND_RESPONDER_UPDATE = "RESPONDER_UPDATE";
    public static final String PREF_LAST_UPDATE_TS = "bridge_last_update_ts";

    // Alert lifecycle states (FEAT-06)
    public static final String ALERT_ACTIVE = "ACTIVE";
    public static final String ALERT_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String ALERT_RESOLVED = "RESOLVED";

    // FEAT-04: window (ms) during which a just-sent SOS can be cancelled
    public static final long SOS_CANCEL_WINDOW_MS = 10000;

    // Emergency Types
    public static final String EMERGENCY_FLOOD = "Flood";
    public static final String EMERGENCY_EARTHQUAKE = "Earthquake";
    public static final String EMERGENCY_FIRE = "Fire";
    public static final String EMERGENCY_MEDICAL = "Medical";
    public static final String EMERGENCY_CYCLONE = "Cyclone";
    public static final String EMERGENCY_OTHER = "Other";

    // Notification
    public static final String CHANNEL_ID = "rescuelink_mesh_channel";
    public static final int NOTIFICATION_ID = 1001;

    // High-importance channel for incoming SOS alerts (TASK-09)
    public static final String SOS_CHANNEL_ID = "rescuelink_sos_channel";
    public static final int SOS_NOTIFICATION_BASE_ID = 2000;

    // SharedPreferences
    public static final String PREFS_NAME = "rescuelink_prefs";
    public static final String PREF_USER_NAME = "user_name";
    public static final String PREF_DEVICE_ID = "device_id";
    // FEAT-01: first-run onboarding completion flag
    public static final String PREF_ONBOARDED = "onboarded";
    // FEAT-08: settings toggles
    public static final String PREF_MESH_ENABLED = "mesh_enabled";
    public static final String PREF_BATTERY_SAVER = "battery_saver";

    // Low Battery Threshold
    public static final int LOW_BATTERY_THRESHOLD = 15;

    // FEAT-09: battery-aware discovery duty cycle (ms)
    public static final long LOW_POWER_SCAN_WINDOW_MS = 15000; // scan for 15s
    public static final long LOW_POWER_SCAN_PAUSE_MS = 45000;  // then pause 45s

    // FEAT-SIREN-02: audible SOS siren
    public static final long SIREN_INTERVAL_NORMAL_MS = 5000;      // pulse every 5s
    public static final long SIREN_INTERVAL_LOW_BATTERY_MS = 8000; // stretch gap when low
    public static final long SIREN_PULSE_DURATION_MS = 700;        // each beep length
    public static final long SIREN_MAX_DURATION_MS = 120000;       // hard cap: auto-stop after 2 min
    public static final String PREF_SIREN_ENABLED_OUTGOING = "siren_outgoing"; // default true
    public static final String PREF_SIREN_ENABLED_INCOMING = "siren_incoming"; // default FALSE (safety)

    // Location update interval (ms)
    public static final long LOCATION_UPDATE_INTERVAL = 10000;
    public static final long LOCATION_FASTEST_INTERVAL = 5000;

    // WorkManager
    public static final String RELAY_WORK_TAG = "message_relay_work";
    public static final long RELAY_INTERVAL_MINUTES = 15;
}
