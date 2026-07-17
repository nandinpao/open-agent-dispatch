package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.admin.AdminRuntimeMetricsService;
import com.opensocket.aievent.gateway.netty.admin.AdminEventStore;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeRegistry;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds local Netty transport snapshots for cluster state sync. */
@Service
public class ClusterStateSnapshotService {

    private final GatewayProperties gatewayProperties;
    private final ClusterSyncProperties clusterSyncProperties;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final AgentRegistry agentRegistry;
    private final AdminRuntimeMetricsService adminRuntimeMetricsService;
    private final AdminEventStore adminEventStore;

    public ClusterStateSnapshotService(
            GatewayProperties gatewayProperties,
            ClusterSyncProperties clusterSyncProperties,
            ClusterNodeRegistry clusterNodeRegistry,
            AgentRegistry agentRegistry,
            AdminRuntimeMetricsService adminRuntimeMetricsService,
            AdminEventStore adminEventStore
    ) {
        this.gatewayProperties = gatewayProperties;
        this.clusterSyncProperties = clusterSyncProperties;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.agentRegistry = agentRegistry;
        this.adminRuntimeMetricsService = adminRuntimeMetricsService;
        this.adminEventStore = adminEventStore;
    }

    public ClusterStateSnapshotResponse localSnapshot() {
        var metrics = ClusterNodeRuntimeMetricsResponse.from(adminRuntimeMetricsService.snapshot());
        var node = clusterNodeRegistry.findByNodeId(gatewayProperties.nodeId())
                .map(snapshot -> ClusterNodeResponse.from(snapshot, metrics))
                .orElseGet(() -> ClusterNodeResponse.from(clusterNodeRegistry.refreshSelf(), metrics));

        return new ClusterStateSnapshotResponse(
                gatewayProperties.nodeId(),
                gatewayProperties.environment(),
                gatewayProperties.version(),
                node,
                agentSummary(),
                metrics,
                agentRegistry.list().stream()
                        .map(AgentResponse::from)
                        .limit(clusterSyncProperties.safeMaxAgentsPerNode())
                        .toList(),
                adminEventStore.recent(clusterSyncProperties.safeMaxEventsPerNode()),
                OffsetDateTime.now()
        );
    }

    private AgentSummaryResponse agentSummary() {
        Map<String, Long> byStatus = agentRegistry.countGroupByStatus().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));

        return new AgentSummaryResponse(
                agentRegistry.count(),
                agentRegistry.countByStatus(AgentStatus.ONLINE),
                agentRegistry.countByStatus(AgentStatus.IDLE),
                agentRegistry.countByStatus(AgentStatus.BUSY),
                agentRegistry.countByStatus(AgentStatus.OFFLINE),
                agentRegistry.countByStatus(AgentStatus.TIMEOUT),
                agentRegistry.countByStatus(AgentStatus.ERROR),
                byStatus
        );
    }
}
