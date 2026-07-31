package com.rescuelink.app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.dao.MessageDao;
import com.rescuelink.app.data.entity.MessageEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageRepository {

    private final MessageDao messageDao;
    private final LiveData<List<MessageEntity>> allMessages;
    private final LiveData<List<MessageEntity>> sosAlerts;
    private final LiveData<List<MessageEntity>> chatMessages;
    private final ExecutorService executor;

    public MessageRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        messageDao = db.messageDao();
        allMessages = messageDao.getAllMessages();
        sosAlerts = messageDao.getSOSAlerts();
        chatMessages = messageDao.getChatMessages();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<MessageEntity>> getAllMessages() { return allMessages; }
    public LiveData<List<MessageEntity>> getSOSAlerts() { return sosAlerts; }
    public LiveData<List<MessageEntity>> getChatMessages() { return chatMessages; }

    public void insert(MessageEntity message) {
        executor.execute(() -> messageDao.insert(message));
    }

    public boolean messageExists(String messageId) {
        return messageDao.messageExists(messageId) > 0;
    }

    public List<MessageEntity> getPendingRelayMessages() {
        return messageDao.getPendingRelayMessages();
    }

    public void markAsRelayed(String messageId) {
        executor.execute(() -> messageDao.markAsRelayed(messageId));
    }
}
