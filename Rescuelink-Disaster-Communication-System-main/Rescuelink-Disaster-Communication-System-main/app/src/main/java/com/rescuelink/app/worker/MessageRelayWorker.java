package com.rescuelink.app.worker;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.service.MeshNetworkService;

import java.util.List;

/**
 * WorkManager task with two real jobs:
 *
 *  1) TASK-06 mesh re-broadcast: if there are pending (isRelayed = 0) messages, signal the
 *     foreground MeshNetworkService to re-broadcast them to connected peers.
 *  2) BRIDGE-CORE opportunistic upload: push any unsynced SOS to the backend. On failure
 *     (offline / server down) return retry() so WorkManager applies exponential backoff.
 *
 * Both are offline-safe: mesh re-broadcast needs no internet, and the upload simply fails
 * quietly and retries later. Neither is on the SOS-send critical path.
 */
public class MessageRelayWorker extends Worker {

    private static final String TAG = "MessageRelayWorker";

    public MessageRelayWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        // 1) Mesh re-broadcast (offline path). Failures here must not stop the bridge attempt.
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            List<com.rescuelink.app.data.entity.MessageEntity> pending =
                    db.messageDao().getPendingRelayMessages();
            if (!pending.isEmpty()) {
                Intent intent = new Intent(ctx, MeshNetworkService.class);
                intent.setAction(MeshNetworkService.ACTION_REBROADCAST_PENDING);
                ContextCompat.startForegroundService(ctx, intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Mesh re-broadcast step failed: " + e.getMessage());
        }

        // 2) Opportunistic backend bridge upload (needs internet; retries with backoff).
        try {
            boolean done = new com.rescuelink.app.bridge.ConnectivityBridgeManager(ctx)
                    .uploadUnsyncedBlocking();
            return done ? Result.success() : Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Bridge upload step failed: " + e.getMessage());
            return Result.retry();
        }
    }
}
