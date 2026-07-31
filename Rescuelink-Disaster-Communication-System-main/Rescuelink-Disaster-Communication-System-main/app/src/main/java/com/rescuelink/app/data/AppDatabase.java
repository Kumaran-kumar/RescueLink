package com.rescuelink.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.rescuelink.app.data.dao.AlertDao;
import com.rescuelink.app.data.dao.ContactDao;
import com.rescuelink.app.data.dao.MessageDao;
import com.rescuelink.app.data.dao.SavedLocationDao;
import com.rescuelink.app.data.dao.UserProfileDao;
import com.rescuelink.app.data.entity.AlertEntity;
import com.rescuelink.app.data.entity.ContactEntity;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.data.entity.SavedLocationEntity;
import com.rescuelink.app.data.entity.UserProfileEntity;

// v2: added UserProfileEntity (FEAT-01) plus new columns on MessageEntity/AlertEntity.
// v3: added SavedLocationEntity (FEAT-LOC-01). Destructive migration is acceptable here —
// mesh data is transient; saved places are the only durable user data and re-entered easily.
// v4: added AlertEntity.archived (SH-06 swipe-to-archive).
// v5: added MessageEntity.syncedToServer (BRIDGE-CORE opportunistic backend upload).
@Database(entities = {MessageEntity.class, AlertEntity.class, ContactEntity.class,
        UserProfileEntity.class, SavedLocationEntity.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract MessageDao messageDao();
    public abstract AlertDao alertDao();
    public abstract ContactDao contactDao();
    public abstract UserProfileDao userProfileDao();
    public abstract SavedLocationDao savedLocationDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "rescuelink_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
