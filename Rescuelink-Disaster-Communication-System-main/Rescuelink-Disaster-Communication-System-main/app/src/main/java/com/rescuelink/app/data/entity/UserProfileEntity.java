package com.rescuelink.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * FEAT-01: the local user's profile. A single-row table (id is always
 * {@link #PROFILE_ID}) holding the responder-relevant details that are attached
 * to outgoing SOS alerts. The display name also lives in SharedPreferences for
 * fast, synchronous access on the mesh/network path.
 */
@Entity(tableName = "user_profile")
public class UserProfileEntity {

    /** Fixed primary key for the single profile row. */
    public static final int PROFILE_ID = 1;

    @PrimaryKey
    private int id = PROFILE_ID;

    private String displayName;
    private String bloodGroup;
    private String medicalNote;
    /** References an emergency ContactEntity by id (0 = none). */
    private int emergencyContactId;

    public UserProfileEntity() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getMedicalNote() { return medicalNote; }
    public void setMedicalNote(String medicalNote) { this.medicalNote = medicalNote; }

    public int getEmergencyContactId() { return emergencyContactId; }
    public void setEmergencyContactId(int emergencyContactId) { this.emergencyContactId = emergencyContactId; }
}
