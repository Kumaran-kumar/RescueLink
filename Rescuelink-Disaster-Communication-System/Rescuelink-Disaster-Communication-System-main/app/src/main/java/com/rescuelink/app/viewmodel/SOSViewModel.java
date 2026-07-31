package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.data.entity.UserProfileEntity;
import com.rescuelink.app.data.repository.MessageRepository;
import com.rescuelink.app.data.repository.UserProfileRepository;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;

public class SOSViewModel extends AndroidViewModel {

    private final MessageRepository messageRepository;
    private final UserProfileRepository profileRepository;

    public SOSViewModel(@NonNull Application application) {
        super(application);
        messageRepository = new MessageRepository(application);
        profileRepository = new UserProfileRepository(application);
    }

    public LiveData<UserProfileEntity> getProfile() {
        return profileRepository.getProfile();
    }

    /**
     * Create and return an SOS alert message entity. FEAT-01: the caller supplies the
     * medical note and blood group (from the user profile) so responders see them.
     */
    public MessageEntity createSOSAlert(String userName, String emergencyType,
                                         double latitude, double longitude, int batteryLevel,
                                         String bloodGroup, String medicalNote) {
        MessageEntity message = new MessageEntity();
        message.setId(DeviceUtils.generateMessageId());
        message.setSenderId(DeviceUtils.getDeviceId(getApplication()));
        message.setSenderName(userName);

        StringBuilder content = new StringBuilder("🚨 SOS ALERT: ").append(emergencyType)
                .append(" | From: ").append(userName)
                .append(" | Location: ").append(latitude).append(", ").append(longitude)
                .append(" | Battery: ").append(batteryLevel).append("%");
        if (bloodGroup != null && !bloodGroup.isEmpty()) {
            content.append(" | Blood: ").append(bloodGroup);
        }
        if (medicalNote != null && !medicalNote.isEmpty()) {
            content.append(" | Medical: ").append(medicalNote);
        }
        message.setContent(content.toString());

        message.setTimestamp(System.currentTimeMillis());
        message.setLatitude(latitude);
        message.setLongitude(longitude);
        message.setTtl(Constants.SOS_TTL);
        message.setSOSAlert(true);
        message.setEmergencyType(emergencyType);
        message.setRelayed(false);
        message.setMine(true);
        message.setKind(Constants.KIND_SOS);
        message.setBatteryLevel(batteryLevel);
        message.setBloodGroup(bloodGroup);
        message.setMedicalNote(medicalNote);
        message.setHopCount(0);
        return message;
    }

    /**
     * FEAT-04: build a lifecycle-update message (CANCEL or SAFE) that references a prior
     * SOS by id so peers can resolve/retract it across the mesh. Dedup-safe: it carries
     * its own UUID and is stored + relayed like any other message.
     */
    public MessageEntity createLifecycleMessage(String kind, String refSosId, String userName) {
        MessageEntity message = new MessageEntity();
        message.setId(DeviceUtils.generateMessageId());
        message.setSenderId(DeviceUtils.getDeviceId(getApplication()));
        message.setSenderName(userName);
        message.setRefId(refSosId);
        message.setKind(kind);
        message.setTimestamp(System.currentTimeMillis());
        message.setTtl(Constants.SOS_TTL);
        message.setSOSAlert(false); // lifecycle updates are not new alerts themselves
        message.setRelayed(false);
        message.setMine(true);
        message.setHopCount(0);
        if (Constants.KIND_CANCEL.equals(kind)) {
            message.setContent("↩️ SOS cancelled by " + userName);
        } else if (Constants.KIND_SAFE.equals(kind)) {
            message.setContent("✅ " + userName + " is now safe");
        } else if (Constants.KIND_ACK.equals(kind)) {
            message.setContent("🚑 " + userName + " is responding");
        }
        return message;
    }
}
