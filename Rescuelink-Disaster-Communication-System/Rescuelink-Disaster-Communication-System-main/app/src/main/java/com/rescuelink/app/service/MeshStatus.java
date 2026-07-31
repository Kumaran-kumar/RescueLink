package com.rescuelink.app.service;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * SH-02: app-scoped, process-wide mesh status so ANY screen can show the status banner
 * without binding to MeshNetworkService. The service is the single writer; screens are
 * read-only observers.
 *
 *  - connectedCount: -1 = service not running / off, 0 = searching, N = N peers.
 *  - peersSupplier: lets the nearby-devices sheet pull a live peer snapshot when opened.
 */
public final class MeshStatus {

    public interface PeersSupplier {
        List<PeerInfo> getConnectedPeers();
    }

    private static final MutableLiveData<Integer> connectedCount = new MutableLiveData<>(-1);
    private static volatile PeersSupplier peersSupplier;

    private MeshStatus() {}

    public static LiveData<Integer> getConnectedCount() {
        return connectedCount;
    }

    /** Called by MeshNetworkService (any thread) as peers connect/disconnect. */
    public static void setConnectedCount(int count) {
        connectedCount.postValue(count);
    }

    /** Service publishes itself as the peer snapshot source while alive. */
    public static void setPeersSupplier(PeersSupplier supplier) {
        peersSupplier = supplier;
    }

    public static List<PeerInfo> getConnectedPeers() {
        PeersSupplier s = peersSupplier;
        return s != null ? s.getConnectedPeers() : new ArrayList<>();
    }
}
