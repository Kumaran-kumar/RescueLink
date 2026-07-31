package com.rescuelink.app.ui.widget;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.rescuelink.app.R;
import com.rescuelink.app.service.MeshNetworkService;
import com.rescuelink.app.service.MeshStatus;
import com.rescuelink.app.ui.adapter.PeerAdapter;

/**
 * FEAT-02: drives the reusable mesh status banner (view_mesh_status.xml) from the
 * bound MeshNetworkService. Colored dot + text reflect the live peer count; tapping
 * the banner opens the nearby-devices bottom sheet.
 *
 * Usage from an Activity:
 *   controller = new MeshStatusController(this, findViewById(R.id.meshStatusBanner));
 *   // once the service is bound:
 *   controller.bind(meshService);
 */
public class MeshStatusController {

    private final AppCompatActivity activity;
    private final View banner;
    private final View dot;
    private final TextView text;
    private MeshNetworkService service;
    private boolean usingHolder = false;

    public MeshStatusController(AppCompatActivity activity, View banner) {
        this.activity = activity;
        this.banner = banner;
        this.dot = banner.findViewById(R.id.meshStatusDot);
        this.text = banner.findViewById(R.id.meshStatusText);
        banner.setOnClickListener(v -> showNearbySheet());
        render(-1); // "off / not bound yet"
    }

    /** Call once the MeshNetworkService is bound; observes its connected-count LiveData. */
    public void bind(MeshNetworkService service) {
        this.service = service;
        LiveData<Integer> count = service.getConnectedDeviceCount();
        count.observe(activity, this::render);
        render(service.getConnectedCount());
    }

    /**
     * SH-02: bind to the app-scoped MeshStatus holder instead of a service instance, so a
     * screen can show the banner + nearby sheet without binding to MeshNetworkService.
     */
    public void bindToHolder() {
        this.usingHolder = true;
        MeshStatus.getConnectedCount().observe(activity, this::render);
    }

    public void unbind() {
        this.service = null;
        render(-1);
    }

    private void render(int count) {
        int colorRes;
        String label;
        boolean off = count < 0 || (!usingHolder && service == null);
        if (off) {
            colorRes = R.color.text_hint;
            label = activity.getString(R.string.mesh_off);
        } else if (count == 0) {
            colorRes = R.color.status_warning;
            label = activity.getString(R.string.mesh_searching);
        } else {
            colorRes = R.color.status_safe;
            label = activity.getString(R.string.mesh_active, count);
        }
        text.setText(label);
        if (dot.getBackground() != null) {
            dot.getBackground().mutate().setColorFilter(
                    ContextCompat.getColor(activity, colorRes), PorterDuff.Mode.SRC_IN);
        }
    }

    private void showNearbySheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_nearby_devices, null);
        RecyclerView rv = content.findViewById(R.id.rvNearby);
        TextView empty = content.findViewById(R.id.tvNearbyEmpty);

        rv.setLayoutManager(new LinearLayoutManager(activity));
        PeerAdapter adapter = new PeerAdapter();
        rv.setAdapter(adapter);

        java.util.List<com.rescuelink.app.service.PeerInfo> peers =
                service != null ? service.getConnectedPeers() : MeshStatus.getConnectedPeers();
        adapter.setPeers(peers);
        empty.setVisibility(peers.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(peers.isEmpty() ? View.GONE : View.VISIBLE);

        sheet.setContentView(content);
        sheet.show();
    }
}
