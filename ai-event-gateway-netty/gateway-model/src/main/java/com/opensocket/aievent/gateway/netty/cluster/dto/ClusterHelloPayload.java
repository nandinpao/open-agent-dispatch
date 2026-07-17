package com.opensocket.aievent.gateway.netty.cluster.dto;

import java.time.OffsetDateTime;

public record ClusterHelloPayload(
        String nodeId,
        String host,
        int tcpPort,
        int websocketPort,
        int adminPort,
        int clusterUdpPort,
        OffsetDateTime startedAt,
        String internalToken,
        String siteId,
        String siteName,
        String region,
        String zone
) {
    public ClusterHelloPayload(
            String nodeId,
            String host,
            int tcpPort,
            int websocketPort,
            int adminPort,
            int clusterUdpPort,
            OffsetDateTime startedAt
    ) {
        this(nodeId, host, tcpPort, websocketPort, adminPort, clusterUdpPort, startedAt, null, "UNKNOWN", "Unknown Site", "unknown", "unknown-zone");
    }
}
