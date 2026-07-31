package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.SavedLocationEntity;
import com.rescuelink.app.data.repository.SavedLocationRepository;

import java.util.List;

/** FEAT-LOC-01: exposes saved places and persists edits via Room. */
public class SavedLocationViewModel extends AndroidViewModel {

    private final SavedLocationRepository repository;
    private final LiveData<List<SavedLocationEntity>> all;

    public SavedLocationViewModel(@NonNull Application application) {
        super(application);
        repository = new SavedLocationRepository(application);
        all = repository.getAll();
    }

    public LiveData<List<SavedLocationEntity>> getAll() { return all; }

    public void save(SavedLocationEntity location) { repository.insert(location); }

    public void update(SavedLocationEntity location) { repository.update(location); }

    public void deleteById(String id) { repository.deleteById(id); }
}
