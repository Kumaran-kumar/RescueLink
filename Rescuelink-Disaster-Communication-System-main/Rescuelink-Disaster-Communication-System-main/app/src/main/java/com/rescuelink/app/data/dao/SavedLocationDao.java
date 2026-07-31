package com.rescuelink.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.rescuelink.app.data.entity.SavedLocationEntity;

import java.util.List;

@Dao
public interface SavedLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavedLocationEntity location);

    @Update
    void update(SavedLocationEntity location);

    @Delete
    void delete(SavedLocationEntity location);

    @Query("DELETE FROM saved_locations WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM saved_locations ORDER BY isFavorite DESC, createdAt DESC")
    LiveData<List<SavedLocationEntity>> getAll();

    @Query("SELECT * FROM saved_locations WHERE category = :category ORDER BY isFavorite DESC, createdAt DESC")
    LiveData<List<SavedLocationEntity>> getByCategory(String category);
}
