package com.rescuelink.app.bridge;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.rescuelink.app.BuildConfig;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.util.MessageSerializer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BRIDGE-CORE: thin OkHttp wrapper for the opportunistic backend bridge.
 *
 * Offline-safe by construction: short timeouts, all calls are made on background threads
 * by callers, and every method returns a boolean / throws IOException that callers treat
 * as "try again later" — never a crash, never a UI block.
 */
public class BackendClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final Gson gson = new Gson();

    public BackendClient() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    /**
     * POST a batch of SOS messages to /api/ingest. Returns true on HTTP 2xx.
     * Uses the mesh wire schema (via MessageSerializer's Gson exclusion) so no translation
     * layer is needed and syncedToServer never leaks to the server.
     */
    public boolean ingest(@NonNull List<MessageEntity> messages) throws IOException {
        // Build {"messages":[...]} using the SAME field set the mesh serializes.
        StringBuilder sb = new StringBuilder("{\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(MessageSerializer.toJson(messages.get(i)));
        }
        sb.append("]}");

        Request req = new Request.Builder()
                .url(BuildConfig.BACKEND_URL + "/api/ingest")
                .post(RequestBody.create(sb.toString(), JSON))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            return resp.isSuccessful();
        }
    }

    /**
     * TWO-WAY: fetch responder status updates since `sinceMs`. Returns the parsed JSON
     * object {serverTime, updates:[{uuid,senderId,status,statusUpdatedAt}]} or null on failure.
     */
    public UpdatesResponse fetchUpdates(long sinceMs) throws IOException {
        Request req = new Request.Builder()
                .url(BuildConfig.BACKEND_URL + "/api/alerts/updates?since=" + sinceMs)
                .header("X-Auth-Token", BuildConfig.BACKEND_TOKEN)
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            return gson.fromJson(resp.body().string(), UpdatesResponse.class);
        }
    }

    public static class UpdatesResponse {
        public long serverTime;
        public List<Update> updates;
    }

    public static class Update {
        public String uuid;
        public String senderId;
        public String status;
        public long statusUpdatedAt;
    }
}
