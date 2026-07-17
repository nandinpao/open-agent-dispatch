package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;

import java.time.OffsetDateTime;
import java.util.List;

/** Remote Netty transport-state snapshot retained by cluster state sync. */
public record RemoteClusterStateSnapshot(
        String nodeId,
        ClusterNodeSyncStatus syncStatus,
        String lastSyncError,
        ClusterNodeResponse node,
        AgentSummaryResponse agentSummary,
        ClusterNodeRuntimeMetricsResponse runtimeMetrics,
        List<AgentResponse> agents,
        List<AdminEventPayload> recentEvents,
        OffsetDateTime capturedAt,
        OffsetDateTime lastSyncAt,
        OffsetDateTime failedAt
) {
    public static RemoteClusterStateSnapshot success(ClusterStateSnapshotResponse state, OffsetDateTime lastSyncAt) {
        return new RemoteClusterStateSnapshot(
                state.nodeId(),
                ClusterNodeSyncStatus.SYNCED,
                null,
                state.node(),
                state.agentSummary(),
                state.runtimeMetrics() == null ? ClusterNodeRuntimeMetricsResponse.empty(state.nodeId()) : state.runtimeMetrics(),
                state.agents() == null ? List.of() : List.copyOf(state.agents()),
                state.recentEvents() == null ? List.of() : List.copyOf(state.recentEvents()),
                state.capturedAt(),
                lastSyncAt,
                null
        );
    }

    public RemoteClusterStateSnapshot withFailure(String nodeId, String message, OffsetDateTime failedAt) {
        return new RemoteClusterStateSnapshot(
                nodeId,
                ClusterNodeSyncStatus.FAILED,
                message,
                node,
                agentSummary,
                runtimeMetrics == null ? ClusterNodeRuntimeMetricsResponse.empty(nodeId) : runtimeMetrics,
                agents == null ? List.of() : List.copyOf(agents),
                recentEvents == null ? List.of() : List.copyOf(recentEvents),
                capturedAt,
                lastSyncAt,
                failedAt
        );
    }

    public RemoteClusterStateSnapshot markStale(OffsetDateTime checkedAt) {
        return new RemoteClusterStateSnapshot(
                nodeId,
                ClusterNodeSyncStatus.STALE,
                lastSyncError,
                node,
                agentSummary,
                runtimeMetrics == null ? ClusterNodeRuntimeMetricsResponse.empty(nodeId) : runtimeMetrics,
                agents == null ? List.of() : List.copyOf(agents),
                recentEvents == null ? List.of() : List.copyOf(recentEvents),
                capturedAt,
                lastSyncAt,
                checkedAt
        );
    }
}
