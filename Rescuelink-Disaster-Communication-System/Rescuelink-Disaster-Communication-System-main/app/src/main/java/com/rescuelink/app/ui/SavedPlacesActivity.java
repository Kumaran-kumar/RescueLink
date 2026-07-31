package com.rescuelink.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.SavedLocationEntity;
import com.rescuelink.app.service.LocationService;
import com.rescuelink.app.ui.adapter.SavedPlaceAdapter;
import com.rescuelink.app.viewmodel.SavedLocationViewModel;

/**
 * FEAT-LOC-01: list of saved places sorted nearest-first (favorites pinned), with
 * offline distance/bearing and edit/delete. Fully offline.
 */
public class SavedPlacesActivity extends AppCompatActivity implements SavedPlaceAdapter.Listener {

    private SavedLocationViewModel viewModel;
    private LocationService locationService;
    private SavedPlaceAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_places);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvSavedPlaces);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedPlaceAdapter(this);
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(SavedLocationViewModel.class);
        viewModel.getAll().observe(this, places -> {
            adapter.setPlaces(places);
            boolean empty = places == null || places.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        // Live location for distance/bearing sorting.
        locationService = new LocationService(this);
        locationService.startLocationUpdates();
        locationService.getCurrentLocation().observe(this, loc -> {
            if (loc != null) adapter.setCurrentLocation(loc.getLatitude(), loc.getLongitude());
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationService.stopLocationUpdates();
    }

    @Override
    public void onEdit(SavedLocationEntity place) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_place, null);
        TextInputEditText etLabel = dialogView.findViewById(R.id.etPlaceLabel);
        ChipGroup chips = dialogView.findViewById(R.id.chipGroupCategory);
        MaterialSwitch swFav = dialogView.findViewById(R.id.switchFavorite);

        etLabel.setText(place.getLabel());
        swFav.setChecked(place.isFavorite());
        chips.check(chipIdFor(place.getCategory()));

        new AlertDialog.Builder(this)
                .setTitle(R.string.cd_edit_place)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String label = etLabel.getText() != null ? etLabel.getText().toString().trim() : "";
                    if (label.isEmpty()) {
                        Toast.makeText(this, R.string.save_place_needs_label, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    place.setLabel(label);
                    place.setCategory(categoryFromChip(chips.getCheckedChipId()));
                    place.setFavorite(swFav.isChecked());
                    viewModel.update(place);
                    Toast.makeText(this, R.string.save_place_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDelete(SavedLocationEntity place) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_place_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> viewModel.deleteById(place.getId()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int chipIdFor(String category) {
        if (category == null) return R.id.chipCatCustom;
        switch (category) {
            case SavedLocationEntity.CAT_HOME: return R.id.chipCatHome;
            case SavedLocationEntity.CAT_HOSPITAL: return R.id.chipCatHospital;
            case SavedLocationEntity.CAT_CARE_CENTER: return R.id.chipCatCare;
            case SavedLocationEntity.CAT_SHELTER: return R.id.chipCatShelter;
            case SavedLocationEntity.CAT_POLICE: return R.id.chipCatPolice;
            case SavedLocationEntity.CAT_WATER: return R.id.chipCatWater;
            default: return R.id.chipCatCustom;
        }
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
}
