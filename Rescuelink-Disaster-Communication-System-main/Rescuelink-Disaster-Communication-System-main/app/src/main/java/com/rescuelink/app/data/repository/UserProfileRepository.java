package com.rescuelink.app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.dao.UserProfileDao;
import com.rescuelink.app.data.entity.UserProfileEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FEAT-01: single-row user profile access. All writes go through Room off the
 * main thread; the network path can read synchronously via {@link #getProfileSync()}.
 */
public class UserProfileRepository {

    private final UserProfileDao profileDao;
    private final ExecutorService executor;

    public UserProfileRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        profileDao = db.userProfileDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<UserProfileEntity> getProfile() {
        return profileDao.getProfile();
    }

    public void save(UserProfileEntity profile) {
        profile.setId(UserProfileEntity.PROFILE_ID);
        executor.execute(() -> profileDao.upsert(profile));
    }

    /** Must be called off the main thread. */
    public UserProfileEntity getProfileSync() {
        return profileDao.getProfileSync();
    }
}
