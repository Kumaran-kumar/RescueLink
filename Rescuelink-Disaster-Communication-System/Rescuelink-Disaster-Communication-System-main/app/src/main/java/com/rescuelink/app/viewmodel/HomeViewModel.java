package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.AlertEntity;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.data.repository.AlertRepository;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;

import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final AlertRepository alertRepository;
    private final LiveData<List<AlertEntity>> recentAlerts;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        alertRepository = new AlertRepository(application);
        recentAlerts = alertRepository.getRecentAlerts();
    }

    public LiveData<List<AlertEntity>> getRecentAlerts() {
        return recentAlerts;
    }

    // FEAT-06: badge count of active nearby SOS
    public LiveData<Integer> getActiveCount() {
        return alertRepository.getActiveCount();
    }

    // SH-06: archive a resolved alert (swipe on the home list)
    public void archive(String alertId) {
        alertRepository.archive(alertId);
    }

    /**
     * FEAT-06: build an ACK ("I'm responding") message for a given alert so the
     * originator learns help is aware. The caller broadcasts it via the mesh service.
     */
    public MessageEntity createAcknowledge(AlertEntity alert) {
        MessageEntity m = new MessageEntity();
        m.setId(DeviceUtils.generateMessageId());
        m.setSenderId(DeviceUtils.getDeviceId(getApplication()));
        m.setSenderName(DeviceUtils.getUserName(getApplication()));
        m.setRefId(alert.getId());
        m.setKind(Constants.KIND_ACK);
        m.setContent("🚑 " + DeviceUtils.getUserName(getApplication()) + " is responding");
        m.setTimestamp(System.currentTimeMillis());
        m.setTtl(Constants.SOS_TTL);
        m.setSOSAlert(false);
        m.setRelayed(false);
        m.setMine(true);
        m.setHopCount(0);
        return m;
    }
}
