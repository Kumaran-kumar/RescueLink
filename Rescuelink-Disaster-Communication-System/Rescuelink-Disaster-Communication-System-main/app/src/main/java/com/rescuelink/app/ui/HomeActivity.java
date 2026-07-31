package com.rescuelink.app.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.data.entity.AlertEntity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.rescuelink.app.R;
import com.rescuelink.app.service.MeshNetworkService;
import com.rescuelink.app.ui.adapter.AlertAdapter;
import com.rescuelink.app.ui.widget.RadarView;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.viewmodel.HomeViewModel;
import com.rescuelink.app.worker.MessageRelayWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    private HomeViewModel viewModel;
    private MeshNetworkService meshService;
    private boolean isBound = false;

    private TextView tvDeviceCount;
    private TextView tvNetworkStatus;
    private TextView tvNoAlerts;
    private RecyclerView rvAlerts;
    private AlertAdapter alertAdapter;
    private RadarView radarView;
    private com.rescuelink.app.ui.widget.MeshStatusController meshStatus;

    // Permissions launcher
    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) allGranted = false;
                }
                if (allGranted) {
                    startMeshService();
                } else {
                    Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize views
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        tvNoAlerts = findViewById(R.id.tvNoAlerts);
        rvAlerts = findViewById(R.id.rvAlerts);
        radarView = findViewById(R.id.radarView);

        // Setup RecyclerView for alerts
        alertAdapter = new AlertAdapter();
        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        rvAlerts.setAdapter(alertAdapter);

        // SH-06: swipe a RESOLVED alert to archive it (removes from the home list).
        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                        int pos = vh.getBindingAdapterPosition();
                        AlertEntity alert = alertAdapter.getAlertAt(pos);
                        if (alert == null) return;
                        if (!"RESOLVED".equals(alert.getStatus())) {
                            // Only resolved alerts archive; bounce active ones back.
                            alertAdapter.notifyItemChanged(pos);
                            Toast.makeText(HomeActivity.this, R.string.archive_only_resolved, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        viewModel.archive(alert.getId());
                        Toast.makeText(HomeActivity.this, R.string.alert_archived, Toast.LENGTH_SHORT).show();
                    }
                };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvAlerts);

        // FEAT-06: acknowledge broadcasts an ACK so the originator knows help is aware.
        alertAdapter.setOnAcknowledgeListener(alert -> {
            if (isBound && meshService != null) {
                meshService.sendMessage(viewModel.createAcknowledge(alert));
                Toast.makeText(this, R.string.sos_ack_sent, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.mesh_off, Toast.LENGTH_SHORT).show();
            }
        });

        // Setup ViewModel
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // FEAT-06: active-nearby-SOS count badge on the alerts header.
        TextView tvActiveBadge = findViewById(R.id.tvActiveBadge);
        viewModel.getActiveCount().observe(this, count -> {
            if (tvActiveBadge != null) {
                int c = count != null ? count : 0;
                tvActiveBadge.setText(String.valueOf(c));
                tvActiveBadge.setVisibility(c > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });

        viewModel.getRecentAlerts().observe(this, alerts -> {
            if (alerts != null && !alerts.isEmpty()) {
                alertAdapter.setAlerts(alerts);
                tvNoAlerts.setVisibility(android.view.View.GONE);
                rvAlerts.setVisibility(android.view.View.VISIBLE);
            } else {
                tvNoAlerts.setVisibility(android.view.View.VISIBLE);
                rvAlerts.setVisibility(android.view.View.GONE);
            }
        });

        // Quick Action Cards
        CardView cardSOS = findViewById(R.id.cardSOS);
        CardView cardChat = findViewById(R.id.cardChat);
        CardView cardMap = findViewById(R.id.cardMap);
        CardView cardContacts = findViewById(R.id.cardContacts);

        cardSOS.setOnClickListener(v -> startActivity(new Intent(this, SOSActivity.class)));
        cardChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        cardMap.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        cardContacts.setOnClickListener(v -> startActivity(new Intent(this, EmergencyContactsActivity.class)));

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_sos) {
                startActivity(new Intent(this, SOSActivity.class));
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                return true;
            } else if (id == R.id.nav_map) {
                startActivity(new Intent(this, MapActivity.class));
                return true;
            }
            return id == R.id.nav_home;
        });

        // Settings entry (FEAT-01 edit profile / FEAT-08 settings)
        android.view.View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }

        // FEAT-02: mesh status banner
        android.view.View banner = findViewById(R.id.meshStatusBanner);
        if (banner != null) {
            meshStatus = new com.rescuelink.app.ui.widget.MeshStatusController(this, banner);
        }

        // Request permissions and start service
        requestPermissions();

        // Schedule background relay worker
        scheduleRelayWorker();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindMeshService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startMeshService();
        } else {
            permissionsLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void startMeshService() {
        Intent serviceIntent = new Intent(this, MeshNetworkService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindMeshService();
    }

    private void bindMeshService() {
        Intent intent = new Intent(this, MeshNetworkService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshNetworkService.MeshBinder binder = (MeshNetworkService.MeshBinder) service;
            meshService = binder.getService();
            isBound = true;

            // FEAT-02: drive the status banner from the bound service
            if (meshStatus != null) meshStatus.bind(meshService);

            // Observe connected device count
            meshService.getConnectedDeviceCount().observe(HomeActivity.this, count -> {
                tvDeviceCount.setText(String.valueOf(count));
                if (radarView != null) radarView.setDeviceCount(count);
                
                if (count > 0) {
                    tvNetworkStatus.setText(count + " device(s) connected");
                    tvNetworkStatus.setTextColor(ContextCompat.getColor(HomeActivity.this, R.color.status_safe));
                } else {
                    tvNetworkStatus.setText("Scanning for nearby devices…");
                    tvNetworkStatus.setTextColor(ContextCompat.getColor(HomeActivity.this, R.color.text_secondary));
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private void scheduleRelayWorker() {
        PeriodicWorkRequest relayWork = new PeriodicWorkRequest.Builder(
                MessageRelayWorker.class,
                Constants.RELAY_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                Constants.RELAY_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                relayWork
        );
    }
}
