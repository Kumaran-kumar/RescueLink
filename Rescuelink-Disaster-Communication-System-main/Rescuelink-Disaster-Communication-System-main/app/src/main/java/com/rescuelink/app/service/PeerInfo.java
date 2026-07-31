package com.rescuelink.app.service;

/**
 * FEAT-02 / FEAT-09: lightweight snapshot of a connected mesh peer for the
 * nearby-devices sheet. batteryLevel is the peer's last self-reported level
 * (-1 if unknown); lastSeen is when we last received a payload from them.
 */
public class PeerInfo {
    public final String endpointId;
    public final String name;
    public final int batteryLevel;
    public final long lastSeen;

    public PeerInfo(String endpointId, String name, int batteryLevel, long lastSeen) {
        this.endpointId = endpointId;
        this.name = name;
        this.batteryLevel = batteryLevel;
        this.lastSeen = lastSeen;
    }
}
