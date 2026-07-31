package com.rescuelink.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuelink.app.R;
import com.rescuelink.app.data.AppDatabase;
import com.rescuelink.app.data.entity.UserProfileEntity;
import com.rescuelink.app.service.MeshNetworkService;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;
import com.rescuelink.app.viewmodel.ProfileViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FEAT-08: profile editing, mesh on/off, battery-saver, clear history, and an honest
 * about section. All persistent state flows through Room / SharedPreferences.
 */
public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etName, etBloodGroup, etMedicalNote;
    private MaterialSwitch switchMesh, switchBattery;
    private ProfileViewModel profileViewModel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etName = findViewById(R.id.etName);
        etBloodGroup = findViewById(R.id.etBloodGroup);
        etMedicalNote = findViewById(R.id.etMedicalNote);
        switchMesh = findViewById(R.id.switchMesh);
        switchBattery = findViewById(R.id.switchBattery);
        MaterialButton btnSave = findViewById(R.id.btnSaveProfile);
        MaterialButton btnClear = findViewById(R.id.btnClearHistory);
        TextView tvAbout = findViewById(R.id.tvAbout);
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        etName.setText(DeviceUtils.getUserName(this));
        profileViewModel.getProfile().observe(this, profile -> {
            if (profile != null) {
                if (!TextUtils.isEmpty(profile.getDisplayName())) etName.setText(profile.getDisplayName());
                etBloodGroup.setText(profile.getBloodGroup());
                etMedicalNote.setText(profile.getMedicalNote());
            }
        });

        // Toggles reflect persisted state (default: mesh on, battery saver off).
        switchMesh.setChecked(prefs().getBoolean(Constants.PREF_MESH_ENABLED, true));
        switchBattery.setChecked(prefs().getBoolean(Constants.PREF_BATTERY_SAVER, false));

        // FEAT-SIREN-02: siren toggles (outgoing default ON, incoming default OFF).
        MaterialSwitch switchSirenOut = findViewById(R.id.switchSirenOutgoing);
        MaterialSwitch switchSirenIn = findViewById(R.id.switchSirenIncoming);
        switchSirenOut.setChecked(prefs().getBoolean(Constants.PREF_SIREN_ENABLED_OUTGOING, true));
        switchSirenIn.setChecked(prefs().getBoolean(Constants.PREF_SIREN_ENABLED_INCOMING, false));
        switchSirenOut.setOnCheckedChangeListener((b, checked) ->
                prefs().edit().putBoolean(Constants.PREF_SIREN_ENABLED_OUTGOING, checked).apply());
        switchSirenIn.setOnCheckedChangeListener((b, checked) ->
                prefs().edit().putBoolean(Constants.PREF_SIREN_ENABLED_INCOMING, checked).apply());

        btnSave.setOnClickListener(v -> saveProfile());

        switchMesh.setOnCheckedChangeListener((b, checked) -> {
            prefs().edit().putBoolean(Constants.PREF_MESH_ENABLED, checked).apply();
            Intent svc = new Intent(this, MeshNetworkService.class);
            if (checked) {
                androidx.core.content.ContextCompat.startForegroundService(this, svc);
            } else {
                stopService(svc);
            }
        });

        switchBattery.setOnCheckedChangeListener((b, checked) -> {
            prefs().edit().putBoolean(Constants.PREF_BATTERY_SAVER, checked).apply();
            // FEAT-09: ask the running service to re-apply its discovery duty cycle.
            if (prefs().getBoolean(Constants.PREF_MESH_ENABLED, true)) {
                Intent svc = new Intent(this, MeshNetworkService.class);
                svc.setAction(MeshNetworkService.ACTION_APPLY_POWER_MODE);
                androidx.core.content.ContextCompat.startForegroundService(this, svc);
            }
        });

        btnClear.setOnClickListener(v -> confirmClearHistory());

        tvAbout.setText(getString(R.string.app_name) + " " + versionName()
                + "\n\nOffline peer-to-peer disaster mesh. SOS + chat over Nearby Connections with"
                + " store-carry-forward relay. Rule-based priority triage (not AI)."
                + "\n\nHow it works (closed loop):\n"
                + "Victim → offline mesh (multi-hop) → whichever phone gets signal bridges out\n"
                + "→ rescuer dashboard → responder status hops back through the mesh → victim.\n\n"
                + "The mesh works fully offline; the internet bridge is optional and never blocks"
                + " sending an SOS. No accounts; data leaves your device only when you bridge an"
                + " SOS to responders.");
    }

    private void saveProfile() {
        String name = text(etName);
        if (!TextUtils.isEmpty(name)) DeviceUtils.setUserName(this, name);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setDisplayName(TextUtils.isEmpty(name) ? DeviceUtils.getUserName(this) : name);
        profile.setBloodGroup(text(etBloodGroup));
        profile.setMedicalNote(text(etMedicalNote));
        profileViewModel.save(profile);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.settings_clear_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> executor.execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                    db.messageDao().deleteAll();
                    db.alertDao().deleteAll();
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show());
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
    }

    private String text(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
