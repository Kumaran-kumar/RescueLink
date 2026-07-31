package com.rescuelink.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.rescuelink.app.data.entity.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(MessageEntity message);

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    LiveData<List<MessageEntity>> getAllMessages();

    @Query("SELECT * FROM messages WHERE isSOSAlert = 1 ORDER BY timestamp DESC")
    LiveData<List<MessageEntity>> getSOSAlerts();

    @Query("SELECT * FROM messages WHERE isSOSAlert = 0 ORDER BY timestamp DESC")
    LiveData<List<MessageEntity>> getChatMessages();

    @Query("SELECT COUNT(*) FROM messages WHERE id = :messageId")
    int messageExists(String messageId);

    // getUnrelayedMessages() (WHERE isRelayed=0 AND isMine=0) was removed (TASK-01):
    // it excluded the device's own messages from store-carry-forward. Use
    // getPendingRelayMessages() below instead.

    /**
     * All messages that have not yet been relayed to any peer, regardless of
     * whether they originated on this device (isMine). This is the store-carry-forward
     * queue: a device's own SOS (isMine = 1) must also be forwarded when a peer later
     * comes into range.
     */
    @Query("SELECT * FROM messages WHERE isRelayed = 0 ORDER BY timestamp ASC")
    List<MessageEntity> getPendingRelayMessages();

    /**
     * Most-recent message IDs, used to seed the in-memory seen-set on startup so
     * previously stored messages are not re-processed and re-broadcast after a restart.
     */
    @Query("SELECT id FROM messages ORDER BY timestamp DESC LIMIT :limit")
    List<String> getRecentMessageIds(int limit);

    @Query("UPDATE messages SET isRelayed = 1 WHERE id = :messageId")
    void markAsRelayed(String messageId);

    // BRIDGE-CORE: SOS alerts not yet uploaded to the backend (any origin — whoever gets
    // signal first carries everyone's queued SOS out). Oldest first.
    @Query("SELECT * FROM messages WHERE isSOSAlert = 1 AND syncedToServer = 0 ORDER BY timestamp ASC")
    List<MessageEntity> getUnsyncedSosMessages();

    @Query("UPDATE messages SET syncedToServer = 1 WHERE id = :messageId")
    void markSynced(String messageId);

    @Query("DELETE FROM messages")
    void deleteAll();
}
