package com.rescuelink.app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.dao.SavedLocationDao;
import com.rescuelink.app.data.entity.SavedLocationEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** FEAT-LOC-01: Room-backed access to user-saved places. All writes off the main thread. */
public class SavedLocationRepository {

    private final SavedLocationDao dao;
    private final LiveData<List<SavedLocationEntity>> all;
    private final ExecutorService executor;

    public SavedLocationRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        dao = db.savedLocationDao();
        all = dao.getAll();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<SavedLocationEntity>> getAll() { return all; }

    public LiveData<List<SavedLocationEntity>> getByCategory(String category) {
        return dao.getByCategory(category);
    }

    public void insert(SavedLocationEntity location) {
        executor.execute(() -> dao.insert(location));
    }

    public void update(SavedLocationEntity location) {
        executor.execute(() -> dao.update(location));
    }

    public void deleteById(String id) {
        executor.execute(() -> dao.deleteById(id));
    }
}
