package com.opensocket.aievent.gateway.netty.cluster.dto;

import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeSnapshot;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterNodeRuntimeMetricsResponse;

import java.time.OffsetDateTime;

/**
 * Immutable DTO for cluster discovery and monitoring payloads. These records are used by UDP
 * discovery, internal state sync, and Admin REST APIs.
 */
public record ClusterNodeResponse(
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
        String remoteAddress,
        ClusterNodeRuntimeMetricsResponse runtimeMetrics
) {
    public static ClusterNodeResponse from(ClusterNodeSnapshot snapshot) {
        return from(snapshot, ClusterNodeRuntimeMetricsResponse.empty(snapshot.nodeId()));
    }

    public static ClusterNodeResponse from(ClusterNodeSnapshot snapshot, ClusterNodeRuntimeMetricsResponse runtimeMetrics) {
        return new ClusterNodeResponse(
                snapshot.nodeId(),
                snapshot.host(),
                snapshot.tcpPort(),
                snapshot.websocketPort(),
                snapshot.adminPort(),
                snapshot.clusterUdpPort(),
                snapshot.status(),
                snapshot.self(),
                snapshot.siteId(),
                snapshot.siteName(),
                snapshot.region(),
                snapshot.zone(),
                snapshot.roleLabel(),
                snapshot.startedAt(),
                snapshot.firstSeenAt(),
                snapshot.lastSeenAt(),
                snapshot.lastMessageType(),
                snapshot.remoteAddress(),
                runtimeMetrics == null ? ClusterNodeRuntimeMetricsResponse.empty(snapshot.nodeId()) : runtimeMetrics
        );
    }
}
