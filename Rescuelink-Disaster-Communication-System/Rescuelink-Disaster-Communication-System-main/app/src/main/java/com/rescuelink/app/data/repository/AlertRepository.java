package com.rescuelink.app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.dao.AlertDao;
import com.rescuelink.app.data.entity.AlertEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertRepository {

    private final AlertDao alertDao;
    private final LiveData<List<AlertEntity>> allAlerts;
    private final LiveData<List<AlertEntity>> recentAlerts;
    private final ExecutorService executor;

    public AlertRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        alertDao = db.alertDao();
        allAlerts = alertDao.getAllAlerts();
        recentAlerts = alertDao.getRecentAlerts();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<AlertEntity>> getAllAlerts() { return allAlerts; }
    public LiveData<List<AlertEntity>> getRecentAlerts() { return recentAlerts; }

    // FEAT-06
    public LiveData<Integer> getActiveCount() { return alertDao.getActiveCount(); }
    public LiveData<List<AlertEntity>> getActiveAlerts() { return alertDao.getActiveAlerts(); }

    public void resolve(String alertId) {
        executor.execute(() -> alertDao.resolveById(alertId));
    }

    // SH-06: swipe-to-archive
    public void archive(String alertId) {
        executor.execute(() -> alertDao.archiveById(alertId));
    }

    public void insert(AlertEntity alert) {
        executor.execute(() -> alertDao.insert(alert));
    }

    public boolean alertExists(String alertId) {
        return alertDao.alertExists(alertId) > 0;
    }
}
