package com.rescuelink.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * FEAT-LOC-01: a user-pinned place (home, hospital, shelter, etc.) used for offline
 * bearing + distance navigation when there is no internet routing. Purely local data.
 */
@Entity(tableName = "saved_locations")
public class SavedLocationEntity {

    // Category constants (stored as strings).
    public static final String CAT_HOME = "HOME";
    public static final String CAT_HOSPITAL = "HOSPITAL";
    public static final String CAT_CARE_CENTER = "CARE_CENTER";
    public static final String CAT_SHELTER = "SHELTER";
    public static final String CAT_POLICE = "POLICE";
    public static final String CAT_WATER = "WATER";
    public static final String CAT_CUSTOM = "CUSTOM";

    @PrimaryKey
    @NonNull
    private String id;

    private String label;
    private String category;
    private double latitude;
    private double longitude;
    private long createdAt;
    private boolean isFavorite;

    public SavedLocationEntity() {}

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}
