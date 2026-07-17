package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeRegistry;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds Admin UI cluster overview from local and remote Netty transport snapshots. */
@Service
public class ClusterOverviewService {

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterSyncProperties clusterSyncProperties;
    private final ClusterStateSnapshotService clusterStateSnapshotService;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final ClusterRemoteStateRegistry clusterRemoteStateRegistry;
    private final ClusterPeerRelationService clusterPeerRelationService;

    public ClusterOverviewService(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterSyncProperties clusterSyncProperties,
            ClusterStateSnapshotService clusterStateSnapshotService,
            ClusterNodeRegistry clusterNodeRegistry,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            ClusterPeerRelationService clusterPeerRelationService
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterSyncProperties = clusterSyncProperties;
        this.clusterStateSnapshotService = clusterStateSnapshotService;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.clusterRemoteStateRegistry = clusterRemoteStateRegistry;
        this.clusterPeerRelationService = clusterPeerRelationService;
    }

    public ClusterOverviewResponse overview() {
        var local = clusterStateSnapshotService.localSnapshot();
        var remotes = clusterRemoteStateRegistry.list();

        Map<String, Long> agentsByNode = new LinkedHashMap<>();
        Map<String, List<AgentResponse>> agentDetailsByNode = new LinkedHashMap<>();
        Map<String, List<AdminEventPayload>> recentEventsByNode = new LinkedHashMap<>();
        Map<String, Long> eventsByNode = new LinkedHashMap<>();
        Map<String, Map<String, Long>> eventTypeCountsByNode = new LinkedHashMap<>();
        Map<String, ClusterNodeRuntimeMetricsResponse> runtimeMetricsByNode = new LinkedHashMap<>();

        var agentNodeGroups = new java.util.ArrayList<ClusterAgentNodeGroupResponse>();
        var eventNodeGroups = new java.util.ArrayList<ClusterEventNodeGroupResponse>();
        agentNodeGroups.add(ClusterAgentNodeGroupResponse.local(local));
        eventNodeGroups.add(ClusterEventNodeGroupResponse.local(local));

        agentsByNode.put(local.nodeId(), local.agentSummary().total());
        agentDetailsByNode.put(local.nodeId(), safeAgents(local.agents()));
        recentEventsByNode.put(local.nodeId(), safeEvents(local.recentEvents()));
        eventsByNode.put(local.nodeId(), (long) safeEvents(local.recentEvents()).size());
        eventTypeCountsByNode.put(local.nodeId(), eventCountsByType(local.recentEvents()));
        runtimeMetricsByNode.put(local.nodeId(), safeMetrics(local.nodeId(), local.runtimeMetrics()));

        long totalAgents = local.agentSummary().total();

        for (var remote : remotes) {
            long remoteAgents = remote.agentSummary() == null ? 0 : remote.agentSummary().total();
            agentsByNode.put(remote.nodeId(), remoteAgents);
            agentDetailsByNode.put(remote.nodeId(), safeAgents(remote.agents()));
            recentEventsByNode.put(remote.nodeId(), safeEvents(remote.recentEvents()));
            eventsByNode.put(remote.nodeId(), (long) safeEvents(remote.recentEvents()).size());
            eventTypeCountsByNode.put(remote.nodeId(), eventCountsByType(remote.recentEvents()));
            agentNodeGroups.add(ClusterAgentNodeGroupResponse.remote(remote));
            eventNodeGroups.add(ClusterEventNodeGroupResponse.remote(remote));
            runtimeMetricsByNode.put(remote.nodeId(), safeMetrics(remote.nodeId(), remote.runtimeMetrics()));
            totalAgents += remoteAgents;
        }

        return new ClusterOverviewResponse(
                local,
                remotes,
                1L + remotes.size(),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.SYNCED),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.FAILED),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.STALE),
                totalAgents,
                Map.copyOf(agentsByNode),
                immutableAgentMap(agentDetailsByNode),
                immutableEventMap(recentEventsByNode),
                Map.copyOf(eventsByNode),
                immutableNestedLongMap(eventTypeCountsByNode),
                List.copyOf(agentNodeGroups),
                List.copyOf(eventNodeGroups),
                siteOverviews(local, remotes),
                Map.copyOf(runtimeMetricsByNode),
                averageCpu(runtimeMetricsByNode),
                totalMemoryUsed(runtimeMetricsByNode),
                totalMemoryMax(runtimeMetricsByNode),
                clusterPeerRelationService.peerRelations(),
                OffsetDateTime.now()
        );
    }

    public List<ClusterNodeResponse> gatewayNodes() {
        Map<String, ClusterNodeResponse> nodes = new LinkedHashMap<>();
        for (var snapshot : clusterNodeRegistry.list()) {
            nodes.put(snapshot.nodeId(), ClusterNodeResponse.from(snapshot));
        }

        var overview = overview();
        if (overview.localState() != null && overview.localState().node() != null) {
            nodes.put(overview.localState().node().nodeId(), overview.localState().node());
        }
        for (var remote : overview.remoteStates()) {
            if (remote.node() != null) {
                nodes.put(remote.node().nodeId(), remote.node());
            }
        }
        return List.copyOf(nodes.values());
    }

    public List<ClusterSiteOverviewResponse> siteOverviews() {
        var overview = overview();
        return overview.siteOverviews();
    }

    public ClusterAgentsResponse agents() {
        var overview = overview();
        return new ClusterAgentsResponse(
                overview.localState().nodeId(),
                overview.totalAgents(),
                overview.agentsByNode(),
                overview.agentDetailsByNode(),
                overview.agentNodeGroups(),
                OffsetDateTime.now()
        );
    }

    public ClusterEventsResponse events() {
        var overview = overview();
        return new ClusterEventsResponse(
                overview.localState().nodeId(),
                overview.eventsByNode().values().stream().mapToLong(Long::longValue).sum(),
                overview.eventsByNode(),
                overview.eventTypeCountsByNode(),
                overview.recentEventsByNode(),
                overview.eventNodeGroups(),
                OffsetDateTime.now()
        );
    }

    public ClusterSyncStatusResponse syncStatus() {
        return new ClusterSyncStatusResponse(
                clusterRuntimeProperties.enabled(),
                clusterSyncProperties.enabled(),
                clusterSyncProperties.safeIntervalMs(),
                clusterSyncProperties.safeRequestTimeoutMs(),
                clusterSyncProperties.safeRemoteStateTtlMs(),
                clusterSyncProperties.safeMaxAgentsPerNode(),
                clusterRemoteStateRegistry.count(),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.SYNCED),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.FAILED),
                clusterRemoteStateRegistry.countByStatus(ClusterNodeSyncStatus.STALE),
                OffsetDateTime.now()
        );
    }

    private List<ClusterSiteOverviewResponse> siteOverviews(
            ClusterStateSnapshotResponse local,
            List<RemoteClusterStateSnapshot> remotes
    ) {
        Map<String, SiteAccumulator> sites = new LinkedHashMap<>();
        accumulateSite(sites, local.node(), local.agentSummary(), safeEvents(local.recentEvents()).size());
        for (var remote : remotes) {
            accumulateSite(sites, remote.node(), remote.agentSummary(), safeEvents(remote.recentEvents()).size());
        }
        return sites.values().stream()
                .map(SiteAccumulator::toResponse)
                .toList();
    }

    private void accumulateSite(
            Map<String, SiteAccumulator> sites,
            ClusterNodeResponse node,
            com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse agentSummary,
            long recentEventCount
    ) {
        if (node == null) {
            return;
        }
        var siteId = blank(node.siteId()) ? "UNKNOWN" : node.siteId();
        var accumulator = sites.computeIfAbsent(siteId, ignored -> new SiteAccumulator(
                siteId,
                blank(node.siteName()) ? siteId : node.siteName(),
                blank(node.region()) ? "unknown" : node.region(),
                blank(node.zone()) ? "unknown-zone" : node.zone()
        ));
        accumulator.addNode(node);
        if (agentSummary != null) {
            accumulator.agentTotal += agentSummary.total();
            accumulator.agentIdle += agentSummary.idle();
            accumulator.agentBusy += agentSummary.busy();
        }
        accumulator.recentEventCount += recentEventCount;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class SiteAccumulator {
        private final String siteId;
        private final String siteName;
        private final String region;
        private final String zone;
        private final java.util.List<String> nodeIds = new java.util.ArrayList<>();
        private long gatewayTotal;
        private long gatewayUp;
        private long gatewaySuspect;
        private long gatewayOffline;
        private long agentTotal;
        private long agentIdle;
        private long agentBusy;
        private long recentEventCount;

        private SiteAccumulator(String siteId, String siteName, String region, String zone) {
            this.siteId = siteId;
            this.siteName = siteName;
            this.region = region;
            this.zone = zone;
        }

        private void addNode(ClusterNodeResponse node) {
            gatewayTotal++;
            nodeIds.add(node.nodeId());
            if (node.status() == ClusterNodeStatus.SELF || node.status() == ClusterNodeStatus.ONLINE || node.status() == ClusterNodeStatus.DISCOVERED) {
                gatewayUp++;
            } else if (node.status() == ClusterNodeStatus.SUSPECT) {
                gatewaySuspect++;
            } else if (node.status() == ClusterNodeStatus.OFFLINE) {
                gatewayOffline++;
            }
        }

        private ClusterSiteOverviewResponse toResponse() {
            var status = gatewayOffline == gatewayTotal && gatewayTotal > 0
                    ? "OFFLINE"
                    : gatewaySuspect > 0 || gatewayOffline > 0 ? "DEGRADED" : "UP";
            return new ClusterSiteOverviewResponse(
                    siteId,
                    siteName,
                    region,
                    zone,
                    status,
                    gatewayTotal,
                    gatewayUp,
                    gatewaySuspect,
                    gatewayOffline,
                    agentTotal,
                    agentIdle,
                    agentBusy,
                    recentEventCount,
                    List.copyOf(nodeIds),
                    OffsetDateTime.now()
            );
        }
    }

    private List<AgentResponse> safeAgents(List<AgentResponse> agents) {
        return agents == null ? List.of() : List.copyOf(agents);
    }

    private List<AdminEventPayload> safeEvents(List<AdminEventPayload> events) {
        return events == null ? List.of() : List.copyOf(events);
    }

    private Map<String, Long> eventCountsByType(List<AdminEventPayload> events) {
        return safeEvents(events).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        event -> event.eventType() == null ? "UNKNOWN" : event.eventType(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
    }

    private Map<String, List<AgentResponse>> immutableAgentMap(Map<String, List<AgentResponse>> agentsByNode) {
        Map<String, List<AgentResponse>> copy = new LinkedHashMap<>();
        agentsByNode.forEach((nodeId, agents) -> copy.put(nodeId, safeAgents(agents)));
        return Map.copyOf(copy);
    }

    private Map<String, List<AdminEventPayload>> immutableEventMap(Map<String, List<AdminEventPayload>> eventsByNode) {
        Map<String, List<AdminEventPayload>> copy = new LinkedHashMap<>();
        eventsByNode.forEach((nodeId, events) -> copy.put(nodeId, safeEvents(events)));
        return Map.copyOf(copy);
    }

    private Map<String, Map<String, Long>> immutableNestedLongMap(Map<String, Map<String, Long>> nested) {
        Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
        nested.forEach((nodeId, values) -> copy.put(nodeId, values == null ? Map.of() : Map.copyOf(values)));
        return Map.copyOf(copy);
    }

    private ClusterNodeRuntimeMetricsResponse safeMetrics(String nodeId, ClusterNodeRuntimeMetricsResponse metrics) {
        return metrics == null ? ClusterNodeRuntimeMetricsResponse.empty(nodeId) : metrics;
    }

    private double averageCpu(Map<String, ClusterNodeRuntimeMetricsResponse> metricsByNode) {
        if (metricsByNode.isEmpty()) {
            return 0.0;
        }
        var average = metricsByNode.values().stream()
                .mapToDouble(ClusterNodeRuntimeMetricsResponse::cpuUsagePercent)
                .average()
                .orElse(0.0);
        return Math.round(average * 10.0) / 10.0;
    }

    private long totalMemoryUsed(Map<String, ClusterNodeRuntimeMetricsResponse> metricsByNode) {
        return metricsByNode.values().stream()
                .mapToLong(ClusterNodeRuntimeMetricsResponse::memoryUsedMb)
                .sum();
    }

    private long totalMemoryMax(Map<String, ClusterNodeRuntimeMetricsResponse> metricsByNode) {
        return metricsByNode.values().stream()
                .mapToLong(ClusterNodeRuntimeMetricsResponse::memoryMaxMb)
                .sum();
    }
}
