package com.rescuelink.app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.dao.ContactDao;
import com.rescuelink.app.data.entity.ContactEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactRepository {

    private final ContactDao contactDao;
    private final LiveData<List<ContactEntity>> allContacts;
    private final ExecutorService executor;

    public ContactRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        contactDao = db.contactDao();
        allContacts = contactDao.getAllContacts();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ContactEntity>> getAllContacts() { return allContacts; }

    public void insert(ContactEntity contact) {
        executor.execute(() -> contactDao.insert(contact));
    }

    public void deleteById(int contactId) {
        executor.execute(() -> contactDao.deleteById(contactId));
    }
}
