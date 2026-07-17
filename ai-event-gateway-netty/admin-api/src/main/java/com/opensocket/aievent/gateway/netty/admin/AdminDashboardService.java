package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminDashboardResponse;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** Aggregates Netty transport runtime, local Agent connection, cluster, and event-store data. */
@Service
public class AdminDashboardService implements AdminDashboardSnapshotProvider {

    private static final int DASHBOARD_AGENT_LIMIT = 50;
    private static final int DASHBOARD_EVENT_LIMIT = 50;

    private final GatewayStatusService gatewayStatusService;
    private final AgentRegistry agentRegistry;
    private final AdminEventStore adminEventStore;
    private final ClusterOverviewService clusterOverviewService;

    public AdminDashboardService(
            GatewayStatusService gatewayStatusService,
            AgentRegistry agentRegistry,
            AdminEventStore adminEventStore,
            ClusterOverviewService clusterOverviewService
    ) {
        this.gatewayStatusService = gatewayStatusService;
        this.agentRegistry = agentRegistry;
        this.adminEventStore = adminEventStore;
        this.clusterOverviewService = clusterOverviewService;
    }

    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                gatewayStatusService.getStatus(),
                agentSummary(),
                agentRegistry.list().stream()
                        .map(AgentResponse::from)
                        .limit(DASHBOARD_AGENT_LIMIT)
                        .toList(),
                clusterOverviewService.overview(),
                adminEventStore.recent(DASHBOARD_EVENT_LIMIT),
                OffsetDateTime.now()
        );
    }

    @Override
    public Object dashboardSnapshot() {
        return dashboard();
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
