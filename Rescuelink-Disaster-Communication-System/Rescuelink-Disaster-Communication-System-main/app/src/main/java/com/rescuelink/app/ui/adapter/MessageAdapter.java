package com.rescuelink.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.MessageEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<MessageEntity> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageEntity message = messages.get(position);
        String time = timeFormat.format(new Date(message.getTimestamp()));

        // Hide all layouts first
        holder.layoutSent.setVisibility(View.GONE);
        holder.layoutReceived.setVisibility(View.GONE);
        holder.layoutAlert.setVisibility(View.GONE);

        if (message.isSOSAlert()) {
            // SOS Alert - center aligned, highlighted
            holder.layoutAlert.setVisibility(View.VISIBLE);
            holder.tvAlertContent.setText(message.getSenderName() + " - " +
                    message.getEmergencyType() + "\n📍 " +
                    String.format(Locale.US, "%.4f, %.4f", message.getLatitude(), message.getLongitude()) +
                    " | " + time);
        } else if (message.isMine()) {
            // Sent message - right aligned
            holder.layoutSent.setVisibility(View.VISIBLE);
            holder.tvSentMessage.setText(message.getContent());
            holder.tvSentTime.setText(time);
            // FEAT-03 + BRIDGE-CORE delivery status escalation:
            //   Queued (no peers) -> Sent (relayed to a peer) -> Reached responders (backend synced).
            String status;
            if (message.isSyncedToServer()) {
                status = holder.itemView.getContext().getString(R.string.msg_status_reached);
            } else if (message.isRelayed()) {
                status = "✓ Sent";
            } else {
                status = "🕓 Queued";
            }
            holder.tvSentStatus.setText(status);
        } else {
            // Received message - left aligned
            holder.layoutReceived.setVisibility(View.VISIBLE);
            holder.tvSenderName.setText(message.getSenderName());
            holder.tvReceivedMessage.setText(message.getContent());
            holder.tvReceivedTime.setText(time);
            // FEAT-03: hop count (how far this travelled across the mesh)
            int hops = message.getHopCount();
            holder.tvReceivedHops.setText(hops > 0 ? hops + (hops == 1 ? " hop" : " hops") : "");
            holder.tvReceivedHops.setVisibility(hops > 0 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void setMessages(List<MessageEntity> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutSent, layoutReceived, layoutAlert;
        TextView tvSentMessage, tvSentTime, tvSentStatus;
        TextView tvSenderName, tvReceivedMessage, tvReceivedTime, tvReceivedHops;
        TextView tvAlertHeader, tvAlertContent;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);
            layoutAlert = itemView.findViewById(R.id.layoutAlert);
            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
            tvSentStatus = itemView.findViewById(R.id.tvSentStatus);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
            tvReceivedHops = itemView.findViewById(R.id.tvReceivedHops);
            tvAlertHeader = itemView.findViewById(R.id.tvAlertHeader);
            tvAlertContent = itemView.findViewById(R.id.tvAlertContent);
        }
    }
}
