package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.AlertEntity;
import com.rescuelink.app.data.repository.AlertRepository;

import java.util.List;

public class MapViewModel extends AndroidViewModel {

    private final AlertRepository alertRepository;
    private final LiveData<List<AlertEntity>> allAlerts;

    public MapViewModel(@NonNull Application application) {
        super(application);
        alertRepository = new AlertRepository(application);
        // FEAT-05: plot only active (non-resolved) alerts on the map.
        allAlerts = alertRepository.getActiveAlerts();
    }

    public LiveData<List<AlertEntity>> getAllAlerts() {
        return allAlerts;
    }
}
