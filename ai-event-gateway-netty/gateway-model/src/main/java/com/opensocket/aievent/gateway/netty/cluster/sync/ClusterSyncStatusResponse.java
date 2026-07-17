package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.OffsetDateTime;

/** Cluster state-sync status for Netty transport snapshots. */
public record ClusterSyncStatusResponse(
        boolean clusterEnabled,
        boolean syncEnabled,
        long intervalMs,
        long requestTimeoutMs,
        long remoteStateTtlMs,
        int maxAgentsPerNode,
        long remoteStateCount,
        long syncedRemoteNodes,
        long failedRemoteNodes,
        long staleRemoteNodes,
        OffsetDateTime serverTime
) {
}
