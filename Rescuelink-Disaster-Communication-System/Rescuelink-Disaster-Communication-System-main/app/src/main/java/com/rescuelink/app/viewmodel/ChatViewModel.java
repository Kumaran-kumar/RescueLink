package com.rescuelink.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.data.repository.MessageRepository;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;

import java.util.List;

public class ChatViewModel extends AndroidViewModel {

    private final MessageRepository messageRepository;
    private final LiveData<List<MessageEntity>> allMessages;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        messageRepository = new MessageRepository(application);
        allMessages = messageRepository.getAllMessages();
    }

    public LiveData<List<MessageEntity>> getAllMessages() {
        return allMessages;
    }

    /**
     * Create a chat message entity ready for sending.
     */
    public MessageEntity createChatMessage(String content, double latitude, double longitude) {
        MessageEntity message = new MessageEntity();
        message.setId(DeviceUtils.generateMessageId());
        message.setSenderId(DeviceUtils.getDeviceId(getApplication()));
        message.setSenderName(DeviceUtils.getUserName(getApplication()));
        message.setContent(content);
        message.setTimestamp(System.currentTimeMillis());
        message.setLatitude(latitude);
        message.setLongitude(longitude);
        message.setTtl(Constants.DEFAULT_TTL);
        message.setSOSAlert(false);
        message.setRelayed(false);
        message.setMine(true);
        return message;
    }
}
