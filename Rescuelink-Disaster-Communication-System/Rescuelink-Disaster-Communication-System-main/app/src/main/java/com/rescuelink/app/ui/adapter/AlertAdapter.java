package com.rescuelink.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.AlertEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private List<AlertEntity> alerts = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnAcknowledgeListener acknowledgeListener;

    /** FEAT-06: fired when the user taps "I'm responding" on an alert. */
    public interface OnAcknowledgeListener {
        void onAcknowledge(AlertEntity alert);
    }

    public void setOnAcknowledgeListener(OnAcknowledgeListener listener) {
        this.acknowledgeListener = listener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        AlertEntity alert = alerts.get(position);

        // SMART-TRIAGE: explainable priority (mirrors the backend scorer) shown to the victim.
        com.rescuelink.app.util.TriageScorer.Result triage =
                com.rescuelink.app.util.TriageScorer.score(
                        alert.getEmergencyType(), alert.getMedicalNote(),
                        alert.getBatteryLevel(),
                        (System.currentTimeMillis() - alert.getTimestamp()) / 60000.0,
                        alert.getHopCount());
        holder.tvAlertType.setText(triage.tier + " · " + alert.getEmergencyType());
        holder.tvAlertType.setTextColor(androidx.core.content.ContextCompat.getColor(
                holder.itemView.getContext(), tierColor(triage.tier)));
        holder.tvAlertUser.setText(triage.reason);
        holder.tvAlertLocation.setText(String.format(Locale.US, "📍 %.4f, %.4f",
                alert.getLatitude(), alert.getLongitude()));
        holder.tvAlertTime.setText(timeFormat.format(new Date(alert.getTimestamp())));

        // Set emoji based on type
        String emoji = getEmojiForType(alert.getEmergencyType());
        holder.tvAlertEmoji.setText(emoji);

        // FEAT-06: lifecycle status + acknowledge action
        String status = alert.getStatus() != null ? alert.getStatus() : "ACTIVE";
        android.content.Context ctx = holder.itemView.getContext();
        int statusColor;
        switch (status) {
            case "RESOLVED":
                holder.tvAlertStatus.setText("✅ Resolved");
                statusColor = R.color.status_safe;
                break;
            case "ACKNOWLEDGED":
                holder.tvAlertStatus.setText("🚑 Responding");
                statusColor = R.color.status_warning;
                break;
            default:
                holder.tvAlertStatus.setText("🔴 Active");
                statusColor = R.color.status_danger;
                break;
        }
        holder.tvAlertStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, statusColor));

        // Acknowledge only makes sense while the alert is not already resolved.
        boolean canAck = !"RESOLVED".equals(status);
        holder.btnAcknowledge.setVisibility(canAck ? View.VISIBLE : View.GONE);
        holder.btnAcknowledge.setOnClickListener(v -> {
            if (acknowledgeListener != null) acknowledgeListener.onAcknowledge(alert);
        });
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public void setAlerts(List<AlertEntity> alerts) {
        this.alerts = alerts != null ? alerts : new ArrayList<>();
        // SMART-TRIAGE: order by priority points (desc), then recency.
        this.alerts.sort((a, b) -> {
            int pa = com.rescuelink.app.util.TriageScorer.score(a.getEmergencyType(), a.getMedicalNote(),
                    a.getBatteryLevel(), (System.currentTimeMillis() - a.getTimestamp()) / 60000.0,
                    a.getHopCount()).points;
            int pb = com.rescuelink.app.util.TriageScorer.score(b.getEmergencyType(), b.getMedicalNote(),
                    b.getBatteryLevel(), (System.currentTimeMillis() - b.getTimestamp()) / 60000.0,
                    b.getHopCount()).points;
            if (pa != pb) return Integer.compare(pb, pa);
            return Long.compare(b.getTimestamp(), a.getTimestamp());
        });
        notifyDataSetChanged();
    }

    private int tierColor(String tier) {
        switch (tier) {
            case com.rescuelink.app.util.TriageScorer.CRITICAL: return R.color.status_danger;
            case com.rescuelink.app.util.TriageScorer.HIGH: return R.color.status_warning;
            default: return R.color.secondary_variant;
        }
    }

    /** SH-06: item at a position, for swipe-to-archive. */
    public AlertEntity getAlertAt(int position) {
        return (position >= 0 && position < alerts.size()) ? alerts.get(position) : null;
    }

    private String getEmojiForType(String type) {
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

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlertEmoji, tvAlertType, tvAlertUser, tvAlertLocation, tvAlertTime, tvAlertStatus;
        com.google.android.material.button.MaterialButton btnAcknowledge;

        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertEmoji = itemView.findViewById(R.id.tvAlertEmoji);
            tvAlertType = itemView.findViewById(R.id.tvAlertType);
            tvAlertUser = itemView.findViewById(R.id.tvAlertUser);
            tvAlertLocation = itemView.findViewById(R.id.tvAlertLocation);
            tvAlertTime = itemView.findViewById(R.id.tvAlertTime);
            tvAlertStatus = itemView.findViewById(R.id.tvAlertStatus);
            btnAcknowledge = itemView.findViewById(R.id.btnAcknowledge);
        }
    }
}
