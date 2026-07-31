package com.rescuelink.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.SavedLocationEntity;
import com.rescuelink.app.util.CategoryMeta;
import com.rescuelink.app.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * FEAT-LOC-01: saved places, sorted nearest-first (favorites pinned to top) with
 * offline distance + bearing from the current location.
 */
public class SavedPlaceAdapter extends RecyclerView.Adapter<SavedPlaceAdapter.VH> {

    public interface Listener {
        void onEdit(SavedLocationEntity place);
        void onDelete(SavedLocationEntity place);
    }

    private List<SavedLocationEntity> places = new ArrayList<>();
    private double curLat = 0.0, curLng = 0.0;
    private boolean haveFix = false;
    private final Listener listener;

    public SavedPlaceAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setPlaces(List<SavedLocationEntity> places) {
        this.places = places != null ? places : new ArrayList<>();
        resort();
    }

    public void setCurrentLocation(double lat, double lng) {
        this.curLat = lat;
        this.curLng = lng;
        this.haveFix = (lat != 0.0 || lng != 0.0);
        resort();
    }

    /** Favorites first, then nearest-first (if we have a fix), else newest-first. */
    private void resort() {
        places.sort((a, b) -> {
            if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;
            if (haveFix) {
                double da = GeoUtils.distanceMeters(curLat, curLng, a.getLatitude(), a.getLongitude());
                double db = GeoUtils.distanceMeters(curLat, curLng, b.getLatitude(), b.getLongitude());
                return Double.compare(da, db);
            }
            return Long.compare(b.getCreatedAt(), a.getCreatedAt());
        });
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_place, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SavedLocationEntity p = places.get(position);
        h.ivCategory.setImageResource(CategoryMeta.iconRes(p.getCategory()));
        h.ivCategory.setColorFilter(ContextCompat.getColor(
                h.itemView.getContext(), CategoryMeta.colorRes(p.getCategory())));
        h.tvLabel.setText((p.isFavorite() ? "⭐ " : "") + p.getLabel());

        String cat = h.itemView.getContext().getString(CategoryMeta.labelRes(p.getCategory()));
        String detail;
        if (haveFix) {
            double dist = GeoUtils.distanceMeters(curLat, curLng, p.getLatitude(), p.getLongitude());
            double bearing = GeoUtils.bearingDegrees(curLat, curLng, p.getLatitude(), p.getLongitude());
            detail = cat + " · " + GeoUtils.formatDistance(dist) + " · " + GeoUtils.formatBearing(bearing);
        } else {
            detail = cat + " · " + h.itemView.getContext().getString(R.string.distance_locating);
        }
        h.tvDetail.setText(detail);

        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(p); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(p); });
    }

    @Override
    public int getItemCount() { return places.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivCategory;
        TextView tvLabel, tvDetail;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            ivCategory = itemView.findViewById(R.id.ivCategory);
            tvLabel = itemView.findViewById(R.id.tvPlaceLabel);
            tvDetail = itemView.findViewById(R.id.tvPlaceDetail);
            btnEdit = itemView.findViewById(R.id.btnEditPlace);
            btnDelete = itemView.findViewById(R.id.btnDeletePlace);
        }
    }
}
