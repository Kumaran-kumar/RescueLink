package com.rescuelink.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.rescuelink.app.util.Constants;

/**
 * Main Application class for RescueLink.
 * 
 * This class initializes global application state and sets up necessary 
 * system components like notification channels required for foreground services.
 */
public class RescueLinkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    /**
     * Creates the notification channel required for the MeshNetworkService.
     * This is necessary for Android 8.0 (Oreo) and above to show foreground notifications.
     */
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                Constants.CHANNEL_ID,
                getString(R.string.mesh_service_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("RescueLink mesh network background service");
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
