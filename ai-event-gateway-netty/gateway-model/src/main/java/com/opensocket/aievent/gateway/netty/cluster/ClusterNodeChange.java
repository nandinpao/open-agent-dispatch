package com.opensocket.aievent.gateway.netty.cluster;

/**
 * Cluster discovery component for Cluster Node Change. It manages Gateway node visibility,
 * UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
public record ClusterNodeChange(
        String nodeId,
        ClusterNodeStatus previousStatus,
        ClusterNodeStatus currentStatus,
        ClusterNodeSnapshot node
) {
    public boolean statusChanged() {
        return previousStatus != currentStatus;
    }
}
