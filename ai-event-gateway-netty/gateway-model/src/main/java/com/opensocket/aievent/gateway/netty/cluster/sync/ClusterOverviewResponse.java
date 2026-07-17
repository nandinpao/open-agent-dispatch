package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.OffsetDateTime;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

import java.util.List;
import java.util.Map;

/** Cluster-wide transport runtime overview for Admin UI. */
public record ClusterOverviewResponse(
        ClusterStateSnapshotResponse localState,
        List<RemoteClusterStateSnapshot> remoteStates,
        long totalNodes,
        long syncedRemoteNodes,
        long failedRemoteNodes,
        long staleRemoteNodes,
        long totalAgents,
        Map<String, Long> agentsByNode,
        Map<String, List<AgentResponse>> agentDetailsByNode,
        Map<String, List<AdminEventPayload>> recentEventsByNode,
        Map<String, Long> eventsByNode,
        Map<String, Map<String, Long>> eventTypeCountsByNode,
        List<ClusterAgentNodeGroupResponse> agentNodeGroups,
        List<ClusterEventNodeGroupResponse> eventNodeGroups,
        List<ClusterSiteOverviewResponse> siteOverviews,
        Map<String, ClusterNodeRuntimeMetricsResponse> runtimeMetricsByNode,
        double averageCpuUsagePercent,
        long totalMemoryUsedMb,
        long totalMemoryMaxMb,
        List<ClusterPeerRelationDto> peerRelations,
        OffsetDateTime generatedAt
) {
}
