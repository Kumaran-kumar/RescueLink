package com.rescuelink.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "alerts")
public class AlertEntity {

    @PrimaryKey
    @NonNull
    private String id; // UUID

    private String userName;
    private double latitude;
    private double longitude;
    private String emergencyType;
    private long timestamp;
    private int batteryLevel;
    private String senderId;

    // FEAT-06: lifecycle state (ACTIVE / ACKNOWLEDGED / RESOLVED)
    private String status;
    // FEAT-05: responder detail carried through for the map detail card
    private String medicalNote;
    private String bloodGroup;
    private int hopCount;
    // SH-06: swipe-to-archive removes a resolved alert from the home list without deleting.
    private boolean archived;

    public AlertEntity() {}

    // Getters and setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMedicalNote() { return medicalNote; }
    public void setMedicalNote(String medicalNote) { this.medicalNote = medicalNote; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
