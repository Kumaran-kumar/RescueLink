package com.rescuelink.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.ContactEntity;
import com.rescuelink.app.data.repository.ContactRepository;
import com.rescuelink.app.ui.adapter.ContactAdapter;

public class EmergencyContactsActivity extends AppCompatActivity {

    private ContactRepository contactRepository;
    private ContactAdapter contactAdapter;
    private RecyclerView rvContacts;
    private TextView tvNoContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        // Init views
        rvContacts = findViewById(R.id.rvContacts);
        tvNoContacts = findViewById(R.id.tvNoContacts);
        MaterialButton btnAdd = findViewById(R.id.btnAddContact);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // Init repository
        contactRepository = new ContactRepository(getApplication());

        // Setup RecyclerView
        contactAdapter = new ContactAdapter();
        contactAdapter.setOnDeleteClickListener(contact -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Contact")
                    .setMessage("Remove " + contact.getName() + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        contactRepository.deleteById(contact.getId());
                        Toast.makeText(this, "Contact removed", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(contactAdapter);

        // Observe contacts
        contactRepository.getAllContacts().observe(this, contacts -> {
            if (contacts != null && !contacts.isEmpty()) {
                contactAdapter.setContacts(contacts);
                tvNoContacts.setVisibility(View.GONE);
                rvContacts.setVisibility(View.VISIBLE);
            } else {
                tvNoContacts.setVisibility(View.VISIBLE);
                rvContacts.setVisibility(View.GONE);
            }
        });

        // SH-02: mesh status banner (holder-backed)
        View meshBanner = findViewById(R.id.meshStatusBanner);
        if (meshBanner != null) {
            new com.rescuelink.app.ui.widget.MeshStatusController(this, meshBanner).bindToHolder();
        }

        // Add button
        btnAdd.setOnClickListener(v -> showAddContactDialog());

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        EditText etName = dialogView.findViewById(R.id.etContactName);
        EditText etPhone = dialogView.findViewById(R.id.etContactPhone);
        EditText etRelation = dialogView.findViewById(R.id.etContactRelation);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String relation = etRelation.getText().toString().trim();

                    if (name.isEmpty() || phone.isEmpty()) {
                        Toast.makeText(this, "Name and phone are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ContactEntity contact = new ContactEntity();
                    contact.setName(name);
                    contact.setPhone(phone);
                    contact.setRelationship(relation);
                    contactRepository.insert(contact);

                    Toast.makeText(this, "Contact saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
