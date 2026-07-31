package com.rescuelink.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.rescuelink.app.data.entity.AlertEntity;

import java.util.List;

@Dao
public interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(AlertEntity alert);

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    LiveData<List<AlertEntity>> getAllAlerts();

    // SH-06: exclude archived alerts from the home list.
    @Query("SELECT * FROM alerts WHERE archived = 0 ORDER BY timestamp DESC LIMIT 10")
    LiveData<List<AlertEntity>> getRecentAlerts();

    @Query("SELECT COUNT(*) FROM alerts WHERE id = :alertId")
    int alertExists(String alertId);

    // SH-06: swipe-to-archive
    @Query("UPDATE alerts SET archived = 1 WHERE id = :alertId")
    void archiveById(String alertId);

    // FEAT-06: lifecycle
    @Query("UPDATE alerts SET status = :status WHERE id = :alertId")
    void setStatus(String alertId, String status);

    /** Resolve any active alert from a given sender (used by CANCEL / "I am safe"). */
    @Query("UPDATE alerts SET status = 'RESOLVED' WHERE senderId = :senderId AND status != 'RESOLVED'")
    void resolveBySender(String senderId);

    @Query("UPDATE alerts SET status = 'RESOLVED' WHERE id = :alertId")
    void resolveById(String alertId);

    @Query("SELECT COUNT(*) FROM alerts WHERE (status = 'ACTIVE' OR status IS NULL) AND archived = 0")
    LiveData<Integer> getActiveCount();

    @Query("SELECT * FROM alerts WHERE (status IS NULL OR status != 'RESOLVED') AND archived = 0 ORDER BY timestamp DESC")
    LiveData<List<AlertEntity>> getActiveAlerts();

    @Query("DELETE FROM alerts")
    void deleteAll();
}
