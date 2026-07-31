package com.rescuelink.app.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.MessageEntity;
import com.rescuelink.app.service.LocationService;
import com.rescuelink.app.service.MeshNetworkService;
import com.rescuelink.app.ui.adapter.MessageAdapter;
import com.rescuelink.app.viewmodel.ChatViewModel;

import java.util.Collections;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ChatViewModel viewModel;
    private MeshNetworkService meshService;
    private LocationService locationService;
    private boolean isBound = false;

    private RecyclerView rvMessages;
    private TextView tvNoMessages;
    private TextView tvPeerCount;
    private EditText etMessage;
    private MessageAdapter messageAdapter;
    private com.rescuelink.app.ui.widget.MeshStatusController meshStatus;

    private double currentLat = 0.0;
    private double currentLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize views
        rvMessages = findViewById(R.id.rvMessages);
        tvNoMessages = findViewById(R.id.tvNoMessages);
        tvPeerCount = findViewById(R.id.tvPeerCount);
        etMessage = findViewById(R.id.etMessage);
        MaterialButton btnSend = findViewById(R.id.btnSend);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // Setup RecyclerView
        messageAdapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Start from bottom like chat apps
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.getAllMessages().observe(this, messages -> {
            if (messages != null && !messages.isEmpty()) {
                // Reverse to show newest at bottom
                List<MessageEntity> reversed = new java.util.ArrayList<>(messages);
                Collections.reverse(reversed);
                messageAdapter.setMessages(reversed);
                tvNoMessages.setVisibility(View.GONE);
                rvMessages.setVisibility(View.VISIBLE);
                rvMessages.scrollToPosition(reversed.size() - 1);
            } else {
                tvNoMessages.setVisibility(View.VISIBLE);
                rvMessages.setVisibility(View.GONE);
            }
        });

        // Location for message metadata
        locationService = new LocationService(this);
        locationService.startLocationUpdates();
        locationService.getCurrentLocation().observe(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
            }
        });

        // FEAT-02: mesh status banner
        View banner = findViewById(R.id.meshStatusBanner);
        if (banner != null) {
            meshStatus = new com.rescuelink.app.ui.widget.MeshStatusController(this, banner);
        }

        // FEAT-07: quick-message templates — tap fills the input for a 1-tap send.
        setupTemplateChip(R.id.chipTrapped, R.string.tmpl_trapped);
        setupTemplateChip(R.id.chipWaterFood, R.string.tmpl_water_food);
        setupTemplateChip(R.id.chipInjured, R.string.tmpl_injured);
        setupTemplateChip(R.id.chipSafeShelter, R.string.tmpl_safe_shelter);

        // Send button
        btnSend.setOnClickListener(v -> sendMessage());

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MeshNetworkService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        locationService.stopLocationUpdates();
    }

    /** FEAT-07: fill the input with a template phrase (user taps send to confirm). */
    private void setupTemplateChip(int chipId, int phraseRes) {
        com.google.android.material.chip.Chip chip = findViewById(chipId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            etMessage.setText(getString(phraseRes));
            etMessage.setSelection(etMessage.getText().length());
        });
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;

        MessageEntity message = viewModel.createChatMessage(content, currentLat, currentLng);

        if (isBound && meshService != null) {
            meshService.sendMessage(message);
        }

        etMessage.setText("");
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshNetworkService.MeshBinder binder = (MeshNetworkService.MeshBinder) service;
            meshService = binder.getService();
            isBound = true;

            if (meshStatus != null) meshStatus.bind(meshService);

            // Observe peer count
            meshService.getConnectedDeviceCount().observe(ChatActivity.this, count -> {
                tvPeerCount.setText(count + " peer" + (count != 1 ? "s" : ""));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };
}
