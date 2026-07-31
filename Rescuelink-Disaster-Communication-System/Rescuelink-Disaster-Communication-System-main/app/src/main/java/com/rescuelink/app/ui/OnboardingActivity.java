package com.rescuelink.app.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.UserProfileEntity;
import com.rescuelink.app.util.Constants;
import com.rescuelink.app.util.DeviceUtils;
import com.rescuelink.app.viewmodel.ProfileViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * FEAT-01: one-time onboarding. Three steps — what the app does, profile setup,
 * and permission priming — then launches Home. Skipped on subsequent launches via
 * the {@link Constants#PREF_ONBOARDED} flag. Reusable as an "Edit profile" target is
 * handled by SettingsActivity instead; this screen only runs on first install.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int STEP_WELCOME = 0;
    private static final int STEP_PROFILE = 1;
    private static final int STEP_PERMISSIONS = 2;

    private int step = STEP_WELCOME;

    private View stepWelcome, stepProfile, stepPermissions;
    private TextInputEditText etName, etBloodGroup, etMedicalNote;
    private MaterialButton btnNext;
    private ProfileViewModel profileViewModel;

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> finishOnboarding());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // UI-05: install the Android 12 splash screen before super/onCreate.
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // If already onboarded, jump straight to Home.
        if (isOnboarded()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        stepWelcome = findViewById(R.id.stepWelcome);
        stepProfile = findViewById(R.id.stepProfile);
        stepPermissions = findViewById(R.id.stepPermissions);
        etName = findViewById(R.id.etName);
        etBloodGroup = findViewById(R.id.etBloodGroup);
        etMedicalNote = findViewById(R.id.etMedicalNote);
        btnNext = findViewById(R.id.btnNext);

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        etName.setText(DeviceUtils.getUserName(this));

        btnNext.setOnClickListener(v -> onNext());
        showStep(STEP_WELCOME);
    }

    private void onNext() {
        if (step == STEP_PROFILE) {
            saveProfile();
        }
        if (step < STEP_PERMISSIONS) {
            showStep(step + 1);
        } else {
            requestPermissions();
        }
    }

    private void showStep(int newStep) {
        step = newStep;
        stepWelcome.setVisibility(step == STEP_WELCOME ? View.VISIBLE : View.GONE);
        stepProfile.setVisibility(step == STEP_PROFILE ? View.VISIBLE : View.GONE);
        stepPermissions.setVisibility(step == STEP_PERMISSIONS ? View.VISIBLE : View.GONE);
        btnNext.setText(step == STEP_PERMISSIONS
                ? getString(R.string.onboarding_grant) : getString(R.string.onboarding_next));
    }

    private void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(name)) {
            DeviceUtils.setUserName(this, name);
        }
        UserProfileEntity profile = new UserProfileEntity();
        profile.setDisplayName(TextUtils.isEmpty(name) ? DeviceUtils.getUserName(this) : name);
        profile.setBloodGroup(text(etBloodGroup));
        profile.setMedicalNote(text(etMedicalNote));
        profileViewModel.save(profile);
    }

    private String text(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
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
        permissionsLauncher.launch(permissions.toArray(new String[0]));
    }

    private void finishOnboarding() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.PREF_ONBOARDED, true)
                .apply();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private boolean isOnboarded() {
        return getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_ONBOARDED, false);
    }
}
