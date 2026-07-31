package com.rescuelink.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.rescuelink.app.data.entity.ContactEntity;

import java.util.List;

@Dao
public interface ContactDao {

    @Insert
    void insert(ContactEntity contact);

    @Delete
    void delete(ContactEntity contact);

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    LiveData<List<ContactEntity>> getAllContacts();

    @Query("DELETE FROM contacts WHERE id = :contactId")
    void deleteById(int contactId);
}
