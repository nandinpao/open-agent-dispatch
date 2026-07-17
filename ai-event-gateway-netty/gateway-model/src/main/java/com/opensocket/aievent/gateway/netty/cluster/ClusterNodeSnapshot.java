package com.opensocket.aievent.gateway.netty.cluster;

import java.time.OffsetDateTime;

/**
 * Cluster discovery component for Cluster Node Snapshot. It manages Gateway node visibility,
 * UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
public record ClusterNodeSnapshot(
        String nodeId,
        String host,
        int tcpPort,
        int websocketPort,
        int adminPort,
        int clusterUdpPort,
        ClusterNodeStatus status,
        boolean self,
        String siteId,
        String siteName,
        String region,
        String zone,
        String roleLabel,
        OffsetDateTime startedAt,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        String lastMessageType,
        String remoteAddress
) {
    /** Backward-compatible constructor for older tests and callers without site metadata. */
    public ClusterNodeSnapshot(
            String nodeId,
            String host,
            int tcpPort,
            int websocketPort,
            int adminPort,
            int clusterUdpPort,
            ClusterNodeStatus status,
            boolean self,
            OffsetDateTime startedAt,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            String lastMessageType,
            String remoteAddress
    ) {
        this(
                nodeId,
                host,
                tcpPort,
                websocketPort,
                adminPort,
                clusterUdpPort,
                status,
                self,
                "UNKNOWN",
                "Unknown Site",
                "unknown",
                "unknown-zone",
                self ? "CURRENT_API_NODE" : "PEER_NODE",
                startedAt,
                firstSeenAt,
                lastSeenAt,
                lastMessageType,
                remoteAddress
        );
    }
}
