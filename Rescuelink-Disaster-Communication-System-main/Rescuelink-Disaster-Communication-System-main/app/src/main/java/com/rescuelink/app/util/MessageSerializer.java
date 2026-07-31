package com.rescuelink.app.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.rescuelink.app.data.entity.MessageEntity;

import java.nio.charset.StandardCharsets;

/**
 * Serializes/deserializes MessageEntity to/from JSON bytes
 * for transmission over Nearby Connections.
 *
 * BRIDGE-CORE: syncedToServer is a LOCAL bookkeeping flag (has this SOS reached the
 * backend?) and must NOT travel over the mesh — excluded here so the wire schema is
 * unchanged and stays identical to what the backend /api/ingest expects.
 */
public class MessageSerializer {

    private static final ExclusionStrategy EXCLUDE_LOCAL = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return "syncedToServer".equals(f.getName());
        }
        @Override
        public boolean shouldSkipClass(Class<?> clazz) { return false; }
    };

    private static final Gson gson = new GsonBuilder()
            .addSerializationExclusionStrategy(EXCLUDE_LOCAL)
            .create();

    /**
     * Serialize a MessageEntity to a JSON byte array.
     */
    public static byte[] serialize(MessageEntity message) {
        String json = gson.toJson(message);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JSON string for a single message (mesh wire schema; syncedToServer excluded).
     * Used by the backend bridge to build an ingest batch.
     */
    public static String toJson(MessageEntity message) {
        return gson.toJson(message);
    }

    /**
     * Deserialize a JSON byte array back to a MessageEntity.
     */
    public static MessageEntity deserialize(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return gson.fromJson(json, MessageEntity.class);
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }
}
