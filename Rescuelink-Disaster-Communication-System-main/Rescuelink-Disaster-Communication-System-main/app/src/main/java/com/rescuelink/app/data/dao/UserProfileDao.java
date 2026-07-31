package com.rescuelink.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.rescuelink.app.data.entity.UserProfileEntity;

@Dao
public interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserProfileEntity profile);

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    LiveData<UserProfileEntity> getProfile();

    /** Synchronous read for the network path (must be called off the main thread). */
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    UserProfileEntity getProfileSync();
}
