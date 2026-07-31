package com.rescuelink.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class MessageEntity {

    @PrimaryKey
    @NonNull
    private String id; // UUID

    private String senderId;
    private String senderName;
    private String content;
    private long timestamp;
    private double latitude;
    private double longitude;
    private int ttl;
    private boolean isSOSAlert;
    private String emergencyType;
    private boolean isRelayed;
    private boolean isMine;

    // FEAT-01: responder-relevant profile data carried in the SOS payload.
    private String medicalNote;
    private String bloodGroup;
    private int batteryLevel;

    // FEAT-03: number of relay hops this message has travelled.
    private int hopCount;

    // FEAT-04 / FEAT-06: message kind and alert lifecycle so cancel / "I am safe" /
    // acknowledge broadcasts can update prior alerts across the mesh.
    // kind: "SOS" | "CHAT" | "CANCEL" | "SAFE" | "ACK"
    private String kind;
    // refId: for CANCEL/SAFE/ACK, the id of the original SOS being updated.
    private String refId;

    // BRIDGE-CORE: set true once this SOS has been uploaded to the backend by a bridge
    // device. Persisted by Room; EXCLUDED from mesh serialization via MessageSerializer's
    // ExclusionStrategy (it's a local bookkeeping flag, not part of the wire schema).
    private boolean syncedToServer;

    public MessageEntity() {}

    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public boolean isSOSAlert() { return isSOSAlert; }
    public void setSOSAlert(boolean SOSAlert) { isSOSAlert = SOSAlert; }

    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }

    public boolean isRelayed() { return isRelayed; }
    public void setRelayed(boolean relayed) { isRelayed = relayed; }

    public boolean isMine() { return isMine; }
    public void setMine(boolean mine) { isMine = mine; }

    public String getMedicalNote() { return medicalNote; }
    public void setMedicalNote(String medicalNote) { this.medicalNote = medicalNote; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public boolean isSyncedToServer() { return syncedToServer; }
    public void setSyncedToServer(boolean syncedToServer) { this.syncedToServer = syncedToServer; }

    /**
     * Defensive shallow copy. Used on the relay path so the object handed to the DB
     * insert is never mutated (TTL decrement, relayed flag) by the relay logic.
     * See MeshNetworkService.processIncomingMessage (TASK-04).
     */
    public MessageEntity copy() {
        MessageEntity c = new MessageEntity();
        c.id = this.id;
        c.senderId = this.senderId;
        c.senderName = this.senderName;
        c.content = this.content;
        c.timestamp = this.timestamp;
        c.latitude = this.latitude;
        c.longitude = this.longitude;
        c.ttl = this.ttl;
        c.isSOSAlert = this.isSOSAlert;
        c.emergencyType = this.emergencyType;
        c.isRelayed = this.isRelayed;
        c.isMine = this.isMine;
        c.medicalNote = this.medicalNote;
        c.bloodGroup = this.bloodGroup;
        c.batteryLevel = this.batteryLevel;
        c.hopCount = this.hopCount;
        c.kind = this.kind;
        c.refId = this.refId;
        return c;
    }
}
