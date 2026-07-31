package com.rescuelink.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.AlertEntity;
import com.rescuelink.app.data.entity.SavedLocationEntity;
import com.rescuelink.app.service.LocationService;
import com.rescuelink.app.util.CategoryMeta;
import com.rescuelink.app.util.DeviceUtils;
import com.rescuelink.app.util.GeoUtils;
import com.rescuelink.app.viewmodel.MapViewModel;
import com.rescuelink.app.viewmodel.SavedLocationViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AppCompatActivity {

    private MapViewModel viewModel;
    private SavedLocationViewModel savedViewModel;
    private LocationService locationService;
    private MapView map;

    private TextView tvMapLocation;
    private TextView tvAlertCount;
    private TextView tvCacheStatus;

    private double currentLat = 0.0;
    private double currentLng = 0.0;

    // FEAT-LOC-01: caches so alerts + saved places can be re-rendered together
    // (overlays are cleared and rebuilt on every change).
    private List<AlertEntity> cachedAlerts = new ArrayList<>();
    private List<SavedLocationEntity> cachedSaved = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Setup osmdroid config BEFORE setContentView
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        
        setContentView(R.layout.activity_map);

        tvMapLocation = findViewById(R.id.tvMapLocation);
        tvAlertCount = findViewById(R.id.tvAlertCount);
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // Setup Map
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        // ViewModels
        viewModel = new ViewModelProvider(this).get(MapViewModel.class);
        savedViewModel = new ViewModelProvider(this).get(SavedLocationViewModel.class);

        // FEAT-LOC-01: Saved Places list screen
        ImageButton btnSavedPlaces = findViewById(R.id.btnSavedPlaces);
        if (btnSavedPlaces != null) {
            btnSavedPlaces.setOnClickListener(v ->
                    startActivity(new Intent(this, SavedPlacesActivity.class)));
        }

        // SH-02: mesh status banner (holder-backed; no service bind needed here)
        View meshBanner = findViewById(R.id.meshStatusBanner);
        if (meshBanner != null) {
            new com.rescuelink.app.ui.widget.MeshStatusController(this, meshBanner).bindToHolder();
        }

        // SH-05: pre-cache the current viewport for offline use.
        tvCacheStatus = findViewById(R.id.tvCacheStatus);
        ImageButton btnCacheArea = findViewById(R.id.btnCacheArea);
        if (btnCacheArea != null) btnCacheArea.setOnClickListener(v -> cacheCurrentArea());

        // FEAT-LOC-01: long-press on the map drops a pin -> save dialog.
        MapEventsOverlay longPressOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) { return false; }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                showSavePlaceDialog(p.getLatitude(), p.getLongitude());
                return true;
            }
        });
        map.getOverlays().add(longPressOverlay);

        Toast.makeText(this, R.string.save_place_long_press_hint, Toast.LENGTH_SHORT).show();

        // Location
        locationService = new LocationService(this);
        locationService.startLocationUpdates();
        locationService.getCurrentLocation().observe(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                tvMapLocation.setText(String.format(Locale.US, "Lat: %.6f, Lng: %.6f", currentLat, currentLng));
                if (currentLat != 0.0) {
                    map.getController().animateTo(new GeoPoint(currentLat, currentLng));
                    refreshOverlays();
                }
            }
        });

        // FEAT-05: active alerts. FEAT-LOC-01: saved places. Both re-render together.
        viewModel.getAllAlerts().observe(this, alerts -> {
            cachedAlerts = alerts != null ? alerts : new ArrayList<>();
            refreshOverlays();
        });
        savedViewModel.getAll().observe(this, saved -> {
            cachedSaved = saved != null ? saved : new ArrayList<>();
            refreshOverlays();
        });
    }

    /**
     * FEAT-05 + FEAT-LOC-01: rebuild all overlays. osmdroid has no per-layer clear, so we
     * wipe and re-add: the long-press receiver, alert markers, saved-place markers, and the
     * own-location pin. Called whenever alerts, saved places, or GPS change.
     */
    private void refreshOverlays() {
        map.getOverlays().clear();

        // Re-add long-press receiver first so it stays active.
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { return false; }
            @Override public boolean longPressHelper(GeoPoint p) {
                showSavePlaceDialog(p.getLatitude(), p.getLongitude());
                return true;
            }
        }));

        tvAlertCount.setText("🚨 " + cachedAlerts.size() + " active alert(s) in your area");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // SH-05: cluster alert + saved markers so they merge into a count bubble when
        // zoomed out and expand as you zoom in.
        RadiusMarkerClusterer clusterer = new RadiusMarkerClusterer(this);
        clusterer.setRadius(120);

        for (AlertEntity alert : cachedAlerts) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(alert.getLatitude(), alert.getLongitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(tintedPin(colorForType(alert.getEmergencyType())));
            marker.setTitle(emojiForType(alert.getEmergencyType()) + " "
                    + alert.getEmergencyType() + " — " + alert.getUserName());
            marker.setSnippet(buildDetail(alert, sdf));
            clusterer.add(marker);
        }

        // FEAT-LOC-01: saved places with category icons + distance/bearing detail cards.
        for (SavedLocationEntity place : cachedSaved) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(place.getLatitude(), place.getLongitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            android.graphics.drawable.Drawable icon =
                    androidx.core.content.ContextCompat.getDrawable(this, CategoryMeta.iconRes(place.getCategory()));
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(androidx.core.content.ContextCompat.getColor(this, CategoryMeta.colorRes(place.getCategory())));
            }
            marker.setIcon(icon);
            marker.setTitle((place.isFavorite() ? "⭐ " : "") + place.getLabel());
            marker.setSnippet(buildSavedDetail(place));
            clusterer.add(marker);
        }

        map.getOverlays().add(clusterer);
        addOwnLocationMarker();
        map.invalidate();
    }

    /** FEAT-LOC-01: detail card text for a saved place (category + offline distance/bearing). */
    private String buildSavedDetail(SavedLocationEntity place) {
        String cat = getString(CategoryMeta.labelRes(place.getCategory()));
        if (currentLat == 0.0 && currentLng == 0.0) {
            return cat + "\n" + getString(R.string.distance_locating);
        }
        double dist = GeoUtils.distanceMeters(currentLat, currentLng, place.getLatitude(), place.getLongitude());
        double bearing = GeoUtils.bearingDegrees(currentLat, currentLng, place.getLatitude(), place.getLongitude());
        return cat + "\n" + GeoUtils.formatDistance(dist) + " · " + GeoUtils.formatBearing(bearing);
    }

    /**
     * SH-05: download the current viewport's tiles for offline use via osmdroid's
     * CacheManager. Caps the zoom depth so it stays a reasonable download; progress and
     * completion are shown in the cache-status indicator.
     */
    private void cacheCurrentArea() {
        BoundingBox bb = map.getBoundingBox();
        int zoomMin = (int) map.getZoomLevelDouble();
        int zoomMax = Math.min(zoomMin + 2, (int) map.getMaxZoomLevel()); // a few levels deeper
        CacheManager cacheManager = new CacheManager(map);

        tvCacheStatus.setVisibility(View.VISIBLE);
        tvCacheStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.status_warning));
        tvCacheStatus.setText(getString(R.string.map_caching));

        cacheManager.downloadAreaAsync(this, bb, zoomMin, zoomMax, new CacheManager.CacheManagerCallback() {
            @Override public void onTaskComplete() {
                tvCacheStatus.setTextColor(androidx.core.content.ContextCompat.getColor(
                        MapActivity.this, R.color.status_safe));
                tvCacheStatus.setText(getString(R.string.map_cached));
            }
            @Override public void onTaskFailed(int errors) {
                tvCacheStatus.setTextColor(androidx.core.content.ContextCompat.getColor(
                        MapActivity.this, R.color.status_danger));
                tvCacheStatus.setText(getString(R.string.map_cache_failed));
            }
            @Override public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {
                tvCacheStatus.setText(getString(R.string.map_caching_progress, progress));
            }
            @Override public void downloadStarted() {}
            @Override public void setPossibleTilesInArea(int total) {}
        });
    }

    /** FEAT-LOC-01: long-press save dialog. */
    private void showSavePlaceDialog(double lat, double lng) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_place, null);
        TextInputEditText etLabel = dialogView.findViewById(R.id.etPlaceLabel);
        ChipGroup chips = dialogView.findViewById(R.id.chipGroupCategory);
        MaterialSwitch swFav = dialogView.findViewById(R.id.switchFavorite);

        new AlertDialog.Builder(this)
                .setTitle(R.string.save_place_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String label = etLabel.getText() != null ? etLabel.getText().toString().trim() : "";
                    if (label.isEmpty()) {
                        Toast.makeText(this, R.string.save_place_needs_label, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SavedLocationEntity place = new SavedLocationEntity();
                    place.setId(DeviceUtils.generateMessageId());
                    place.setLabel(label);
                    place.setCategory(categoryFromChip(chips.getCheckedChipId()));
                    place.setLatitude(lat);
                    place.setLongitude(lng);
                    place.setCreatedAt(System.currentTimeMillis());
                    place.setFavorite(swFav.isChecked());
                    savedViewModel.save(place);
                    Toast.makeText(this, R.string.save_place_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String categoryFromChip(int checkedId) {
        if (checkedId == R.id.chipCatHome) return SavedLocationEntity.CAT_HOME;
        if (checkedId == R.id.chipCatHospital) return SavedLocationEntity.CAT_HOSPITAL;
        if (checkedId == R.id.chipCatCare) return SavedLocationEntity.CAT_CARE_CENTER;
        if (checkedId == R.id.chipCatShelter) return SavedLocationEntity.CAT_SHELTER;
        if (checkedId == R.id.chipCatPolice) return SavedLocationEntity.CAT_POLICE;
        if (checkedId == R.id.chipCatWater) return SavedLocationEntity.CAT_WATER;
        return SavedLocationEntity.CAT_CUSTOM;
    }

    /** FEAT-05: detail card text — medical, battery, time, hops, and offline Navigate. */
    private String buildDetail(AlertEntity alert, SimpleDateFormat sdf) {
        StringBuilder sb = new StringBuilder();
        sb.append("Time: ").append(sdf.format(new Date(alert.getTimestamp())));
        if (alert.getBloodGroup() != null && !alert.getBloodGroup().isEmpty()) {
            sb.append("\nBlood: ").append(alert.getBloodGroup());
        }
        if (alert.getMedicalNote() != null && !alert.getMedicalNote().isEmpty()) {
            sb.append("\nMedical: ").append(alert.getMedicalNote());
        }
        if (alert.getBatteryLevel() > 0) {
            sb.append("\nBattery: ").append(alert.getBatteryLevel()).append('%');
        }
        sb.append("\nHops: ").append(alert.getHopCount());
        sb.append("\nStatus: ").append(alert.getStatus() != null ? alert.getStatus() : "ACTIVE");
        // Offline "Navigate": straight-line bearing + distance (no routing offline).
        if (currentLat != 0.0 || currentLng != 0.0) {
            float[] result = new float[2];
            android.location.Location.distanceBetween(
                    currentLat, currentLng, alert.getLatitude(), alert.getLongitude(), result);
            float meters = result[0];
            float bearing = (result[1] + 360) % 360;
            String dist = meters < 1000
                    ? String.format(Locale.US, "%.0f m", meters)
                    : String.format(Locale.US, "%.1f km", meters / 1000f);
            sb.append("\nNavigate: ").append(compass(bearing)).append(' ').append(dist)
                    .append(String.format(Locale.US, " (%.0f°)", bearing));
        }
        return sb.toString();
    }

    private String compass(float bearing) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(bearing / 45f) % 8];
    }

    private void addOwnLocationMarker() {
        if (currentLat == 0.0 && currentLng == 0.0) return;
        Marker me = new Marker(map);
        me.setPosition(new GeoPoint(currentLat, currentLng));
        me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        me.setIcon(tintedPin(androidx.core.content.ContextCompat.getColor(this, R.color.secondary_variant)));
        me.setTitle("📍 You are here");
        map.getOverlays().add(me);
    }

    private android.graphics.drawable.Drawable tintedPin(int color) {
        android.graphics.drawable.Drawable d =
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.map_pin);
        if (d != null) {
            d = d.mutate();
            d.setTint(color);
        }
        return d;
    }

    private int colorForType(String type) {
        int res;
        if (type == null) { res = R.color.status_danger; }
        else switch (type) {
            case "Flood": res = R.color.flood_color; break;
            case "Earthquake": res = R.color.earthquake_color; break;
            case "Fire": res = R.color.fire_color; break;
            case "Medical": res = R.color.medical_color; break;
            case "Cyclone": res = R.color.cyclone_color; break;
            default: res = R.color.status_danger; break;
        }
        return androidx.core.content.ContextCompat.getColor(this, res);
    }

    private String emojiForType(String type) {
        if (type == null) return "⚠️";
        switch (type) {
            case "Flood": return "🌊";
            case "Earthquake": return "🏚️";
            case "Fire": return "🔥";
            case "Medical": return "🏥";
            case "Cyclone": return "🌀";
            default: return "⚠️";
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationService.stopLocationUpdates();
    }

}
