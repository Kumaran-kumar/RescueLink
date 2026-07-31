package com.rescuelink.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.service.PeerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** FEAT-02: lists connected mesh peers with battery + last-seen in the nearby sheet. */
public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {

    private List<PeerInfo> peers = new ArrayList<>();

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_peer, parent, false);
        return new PeerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        PeerInfo peer = peers.get(position);
        holder.tvName.setText(peer.name != null ? peer.name : peer.endpointId);

        String battery = peer.batteryLevel >= 0 ? peer.batteryLevel + "%" : "—";
        holder.tvBattery.setText(holder.itemView.getContext().getString(R.string.peer_battery, battery));
        holder.tvLastSeen.setText(lastSeen(holder, peer.lastSeen));
    }

    private String lastSeen(PeerViewHolder holder, long ts) {
        if (ts <= 0) return holder.itemView.getContext().getString(R.string.peer_last_seen_connected);
        long secs = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - ts);
        String ago = secs < 60 ? secs + "s" : TimeUnit.SECONDS.toMinutes(secs) + "m";
        return holder.itemView.getContext().getString(R.string.peer_last_seen, ago);
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    public void setPeers(List<PeerInfo> peers) {
        this.peers = peers != null ? peers : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvBattery, tvLastSeen;

        PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPeerName);
            tvBattery = itemView.findViewById(R.id.tvPeerBattery);
            tvLastSeen = itemView.findViewById(R.id.tvPeerLastSeen);
        }
    }
}
