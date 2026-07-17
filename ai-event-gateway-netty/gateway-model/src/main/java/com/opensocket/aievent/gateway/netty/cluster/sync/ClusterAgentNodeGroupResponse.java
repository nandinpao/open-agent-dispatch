package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cluster-wide Agent aggregation DTO for one gateway node.
 *
 * <p>This is a read-only Admin UI projection. It does not imply that the local node owns
 * remote agents or can make routing decisions for them directly.</p>
 */
public record ClusterAgentNodeGroupResponse(
        String nodeId,
        boolean self,
        ClusterNodeSyncStatus syncStatus,
        String freshnessStatus,
        Long syncAgeMs,
        Boolean stale,
        AgentSummaryResponse summary,
        List<AgentResponse> agents,
        OffsetDateTime capturedAt,
        OffsetDateTime lastSyncAt,
        String lastSyncError
) {
    public static ClusterAgentNodeGroupResponse local(ClusterStateSnapshotResponse local) {
        return new ClusterAgentNodeGroupResponse(
                local.nodeId(),
                true,
                ClusterNodeSyncStatus.SYNCED,
                "LOCAL",
                0L,
                false,
                local.agentSummary(),
                local.agents() == null ? List.of() : List.copyOf(local.agents()),
                local.capturedAt(),
                local.capturedAt(),
                null
        );
    }

    public static ClusterAgentNodeGroupResponse remote(RemoteClusterStateSnapshot remote) {
        Long ageMs = elapsedMs(remote.lastSyncAt(), OffsetDateTime.now());
        return new ClusterAgentNodeGroupResponse(
                remote.nodeId(),
                false,
                remote.syncStatus(),
                freshness(remote.syncStatus()),
                ageMs,
                remote.syncStatus() == ClusterNodeSyncStatus.STALE,
                remote.agentSummary(),
                remote.agents() == null ? List.of() : List.copyOf(remote.agents()),
                remote.capturedAt(),
                remote.lastSyncAt(),
                remote.lastSyncError()
        );
    }

    private static String freshness(ClusterNodeSyncStatus status) {
        if (status == ClusterNodeSyncStatus.SYNCED) {
            return "FRESH";
        }
        if (status == ClusterNodeSyncStatus.STALE) {
            return "STALE";
        }
        if (status == ClusterNodeSyncStatus.FAILED) {
            return "FAILED";
        }
        if (status == ClusterNodeSyncStatus.SKIPPED) {
            return "SKIPPED";
        }
        return "UNKNOWN";
    }

    private static Long elapsedMs(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        return Math.max(Duration.between(from, to).toMillis(), 0L);
    }
}
