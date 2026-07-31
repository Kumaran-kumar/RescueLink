package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.UserProfileEntity;
import com.rescuelink.app.data.repository.UserProfileRepository;

/**
 * FEAT-01 / FEAT-08: exposes the single user profile and persists edits via Room.
 */
public class ProfileViewModel extends AndroidViewModel {

    private final UserProfileRepository repository;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
    }

    public LiveData<UserProfileEntity> getProfile() {
        return repository.getProfile();
    }

    public void save(UserProfileEntity profile) {
        repository.save(profile);
    }
}
