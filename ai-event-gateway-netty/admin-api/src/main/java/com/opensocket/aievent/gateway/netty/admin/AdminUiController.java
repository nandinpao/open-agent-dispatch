package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminActionResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminHealthResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminMetricsResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminTraceResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminWebSocketStatusResponse;
import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeRegistry;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterAgentsResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterEventsResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewService;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterRemoteStateRegistry;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryHistoryResponse;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryMetrics;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryRequest;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryService;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketClientType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import com.opensocket.aievent.gateway.netty.api.GatewayApiErrorCode;
import com.opensocket.aievent.gateway.netty.api.GatewayApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Frontend-oriented Admin API facade for ai-event-gateway-netty transport observability.
 *
 * <p>P6.4 keeps task lifecycle APIs removed from this controller. Admin UI should prefer
 * /api/admin/runtime/* for connection, delivery, inbound event, and gateway runtime observability.</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUiController {

    private final GatewayProperties gatewayProperties;
    private final GatewayStatusService gatewayStatusService;
    private final AdminRuntimeMetricsService adminRuntimeMetricsService;
    private final AgentRegistry agentRegistry;
    private final AgentLifecycleService agentLifecycleService;
    private final CommandDeliveryService commandDeliveryService;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final ClusterOverviewService clusterOverviewService;
    private final ClusterRemoteStateRegistry clusterRemoteStateRegistry;
    private final AdminEventStore adminEventStore;
    private final WebSocketAdminBroadcaster adminBroadcaster;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final TcpConnectionRegistry tcpConnectionRegistry;
    private final AdminMaintenanceService adminMaintenanceService;

    public AdminUiController(
            GatewayProperties gatewayProperties,
            GatewayStatusService gatewayStatusService,
            AdminRuntimeMetricsService adminRuntimeMetricsService,
            AgentRegistry agentRegistry,
            AgentLifecycleService agentLifecycleService,
            CommandDeliveryService commandDeliveryService,
            ClusterNodeRegistry clusterNodeRegistry,
            ClusterOverviewService clusterOverviewService,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            AdminEventStore adminEventStore,
            WebSocketAdminBroadcaster adminBroadcaster,
            WebSocketSessionRegistry webSocketSessionRegistry,
            TcpConnectionRegistry tcpConnectionRegistry,
            AdminMaintenanceService adminMaintenanceService
    ) {
        this.gatewayProperties = gatewayProperties;
        this.gatewayStatusService = gatewayStatusService;
        this.adminRuntimeMetricsService = adminRuntimeMetricsService;
        this.agentRegistry = agentRegistry;
        this.agentLifecycleService = agentLifecycleService;
        this.commandDeliveryService = commandDeliveryService;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.clusterOverviewService = clusterOverviewService;
        this.clusterRemoteStateRegistry = clusterRemoteStateRegistry;
        this.adminEventStore = adminEventStore;
        this.adminBroadcaster = adminBroadcaster;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.tcpConnectionRegistry = tcpConnectionRegistry;
        this.adminMaintenanceService = adminMaintenanceService;
    }

    @GetMapping("/health")
    public AdminHealthResponse health() {
        var status = gatewayStatusService.getStatus();
        return new AdminHealthResponse(
                status.status(),
                status.nodeId(),
                status.environment(),
                status.version(),
                status.tcpEnabled(),
                status.websocketEnabled(),
                status.clusterEnabled(),
                Map.of(
                        "tcpActiveConnections", status.tcpActiveConnections(),
                        "websocketActiveSessions", status.websocketActiveSessions(),
                        "agentTotal", status.agentTotal(),
                        "clusterOnlineNodes", status.clusterOnlineNodes()
                ),
                OffsetDateTime.now()
        );
    }

    @GetMapping("/metrics")
    public AdminMetricsResponse metrics() {
        return adminRuntimeMetricsService.snapshot();
    }

    @GetMapping("/delivery/metrics")
    public CommandDeliveryMetrics deliveryMetrics() {
        return commandDeliveryService.metrics();
    }

    @GetMapping("/delivery/history")
    public CommandDeliveryHistoryResponse deliveryHistory(@RequestParam(defaultValue = "100") int limit) {
        return commandDeliveryService.history(limit);
    }

    @GetMapping("/global/overview")
    public ClusterOverviewResponse globalOverview() {
        return clusterOverviewService.overview();
    }

    @GetMapping("/sites")
    public List<com.opensocket.aievent.gateway.netty.cluster.sync.ClusterSiteOverviewResponse> adminSites() {
        return clusterOverviewService.siteOverviews();
    }

    @GetMapping("/sites/{siteId}/overview")
    public com.opensocket.aievent.gateway.netty.cluster.sync.ClusterSiteOverviewResponse adminSiteOverview(@PathVariable String siteId) {
        return clusterOverviewService.siteOverviews().stream()
                .filter(site -> Objects.equals(site.siteId(), siteId == null ? null : siteId.toUpperCase(java.util.Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.NOT_FOUND, "Site not found: " + siteId));
    }

    @GetMapping("/gateways")
    public List<ClusterNodeResponse> adminGateways() {
        return adminClusterNodes();
    }

    @GetMapping("/cluster/nodes")
    public List<ClusterNodeResponse> adminClusterNodes() {
        return clusterOverviewService.gatewayNodes();
    }

    @GetMapping("/agents")
    public List<AgentResponse> adminAgents() {
        return agentRegistry.list().stream()
                .map(AgentResponse::from)
                .toList();
    }

    @GetMapping("/ws/status")
    public AdminWebSocketStatusResponse adminWebSocketStatusAlias() {
        return new AdminWebSocketStatusResponse(
                adminBroadcaster.adminChannelCount(),
                webSocketSessionRegistry.countActiveByType(WebSocketClientType.ADMIN),
                adminEventStore.count(),
                adminEventStore.limit()
        );
    }

    @GetMapping("/agents/{agentId}")
    public AgentResponse adminAgent(@PathVariable String agentId) {
        return agentRegistry.findById(agentId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_AGENT_NOT_FOUND, "Agent not found: " + agentId));
    }

    @PostMapping("/agents/{agentId}/ping")
    public AdminActionResponse pingAgent(@PathVariable String agentId) {
        var agent = agentRegistry.findById(agentId)
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_AGENT_NOT_FOUND, "Agent not found: " + agentId));
        var response = commandDeliveryService.deliverToAgent(agentId, new CommandDeliveryRequest(
                "admin-ping-" + System.currentTimeMillis(),
                MessageType.ADMIN_EVENT,
                Map.of("eventType", "agent.ping", "agentId", agentId, "requestedAt", OffsetDateTime.now().toString()),
                null,
                "admin-ui"
        ));
        adminBroadcaster.broadcast("AGENT_PING_REQUESTED", "Admin requested agent ping", Map.of("agentId", agentId, "deliveryStatus", response.deliveryStatus().name()));
        return AdminActionResponse.completed(
                "AGENT_PING",
                "AGENT",
                agent.agentId(),
                response.message(),
                Map.of("deliveryStatus", response.deliveryStatus().name())
        );
    }

    @PostMapping("/agents/{agentId}/disconnect")
    public AdminActionResponse disconnectAgent(@PathVariable String agentId,
                                               @RequestBody(required = false) DisconnectAgentRequest request) {
        var agent = agentRegistry.findById(agentId)
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_AGENT_NOT_FOUND, "Agent not found on this Netty node: " + agentId));
        boolean closed = false;
        if (agent.connectionType() == ConnectionType.TCP) {
            closed = tcpConnectionRegistry.closeChannel(agent.connectionId());
            agentLifecycleService.markOfflineByTcpConnection(agent.connectionId());
        } else if (agent.connectionType() == ConnectionType.WEBSOCKET) {
            closed = webSocketSessionRegistry.closeChannel(agent.sessionId());
            agentLifecycleService.markOfflineByWebSocketSession(agent.sessionId());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("closed", closed);
        data.put("localNodeOnly", true);
        data.put("nodeId", gatewayProperties.nodeId());
        data.put("gatewayNodeId", gatewayProperties.nodeId());
        data.put("connectionType", agent.connectionType() == null ? "UNKNOWN" : agent.connectionType().name());
        data.put("connectionId", agent.connectionId() == null ? "" : agent.connectionId());
        data.put("sessionId", agent.sessionId() == null ? "" : agent.sessionId());
        data.put("requestedBy", request == null || request.requestedBy() == null ? "admin-api" : request.requestedBy());
        data.put("reason", request == null || request.reason() == null ? "Runtime disconnect requested" : request.reason());
        adminBroadcaster.broadcast("AGENT_DISCONNECT_REQUESTED", "Agent disconnect requested", Map.of("agentId", agentId, "closed", closed, "nodeId", gatewayProperties.nodeId()));
        return AdminActionResponse.completed(
                "AGENT_DISCONNECT",
                "AGENT",
                agentId,
                closed ? "Agent connection close requested by Netty owner node." : "Agent was found, but no active channel could be closed on this Netty node.",
                data
        );
    }

    public record DisconnectAgentRequest(String requestedBy, String reason) {}

    @GetMapping("/traces/{traceId}")
    public AdminTraceResponse trace(@PathVariable String traceId, @RequestParam(defaultValue = "200") int eventLimit) {
        var events = adminEventStore.recent(eventLimit).stream()
                .filter(event -> matchesTrace(event, traceId))
                .toList();
        return new AdminTraceResponse(
                traceId,
                events,
                Map.of("eventCount", events.size()),
                OffsetDateTime.now()
        );
    }

    @GetMapping("/cluster/agents")
    public ClusterAgentsResponse adminClusterAgents() {
        return clusterOverviewService.agents();
    }

    @GetMapping("/cluster/agents/by-node")
    public ClusterAgentsResponse adminClusterAgentsByNode() {
        return clusterOverviewService.agents();
    }

    @GetMapping("/cluster/events")
    public ClusterEventsResponse adminClusterEvents() {
        return clusterOverviewService.events();
    }

    @GetMapping("/cluster/events/by-node")
    public ClusterEventsResponse adminClusterEventsByNode() {
        return clusterOverviewService.events();
    }

    @GetMapping("/cluster/topology")
    public Map<String, Object> clusterTopology() {
        ClusterOverviewResponse overview = clusterOverviewService.overview();
        return Map.of(
                "overview", overview,
                "nodes", adminClusterNodes(),
                "drainedNodes", adminMaintenanceService.drainedNodes(),
                "generatedAt", OffsetDateTime.now()
        );
    }

    @GetMapping("/cluster/nodes/{nodeId}")
    public Map<String, Object> adminClusterNode(@PathVariable String nodeId) {
        var local = clusterNodeRegistry.findByNodeId(nodeId).map(ClusterNodeResponse::from).orElse(null);
        var remote = clusterRemoteStateRegistry.findByNodeId(nodeId).orElse(null);
        if (local == null && remote == null) {
            throw new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Cluster node not found: " + nodeId);
        }
        return Map.of(
                "nodeId", nodeId,
                "localDiscovery", local == null ? Map.of() : local,
                "remoteState", remote == null ? Map.of() : remote,
                "drained", adminMaintenanceService.isDrained(nodeId)
        );
    }

    @GetMapping("/cluster/nodes/{nodeId}/agents")
    public List<AgentResponse> adminClusterNodeAgents(@PathVariable String nodeId) {
        if (Objects.equals(nodeId, gatewayProperties.nodeId())) {
            return adminAgents();
        }
        return clusterRemoteStateRegistry.findByNodeId(nodeId)
                .map(remote -> remote.agents() == null ? List.<AgentResponse>of() : remote.agents())
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Remote state not found for node: " + nodeId));
    }

    @GetMapping("/cluster/nodes/{nodeId}/events")
    public List<AdminEventPayload> adminClusterNodeEvents(@PathVariable String nodeId) {
        if (Objects.equals(nodeId, gatewayProperties.nodeId())) {
            return adminEventStore.recent(50);
        }
        return clusterRemoteStateRegistry.findByNodeId(nodeId)
                .map(remote -> remote.recentEvents() == null ? List.<AdminEventPayload>of() : remote.recentEvents())
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Remote state not found for node: " + nodeId));
    }

    @PostMapping("/cluster/nodes/{nodeId}/drain")
    public AdminActionResponse drainNode(@PathVariable String nodeId) {
        requireKnownNode(nodeId);
        var response = adminMaintenanceService.drainNode(nodeId);
        adminBroadcaster.broadcast("CLUSTER_NODE_DRAINED", "Admin marked cluster node drained", Map.of("nodeId", nodeId));
        return response;
    }

    @PostMapping("/cluster/nodes/{nodeId}/resume")
    public AdminActionResponse resumeNode(@PathVariable String nodeId) {
        requireKnownNode(nodeId);
        var response = adminMaintenanceService.resumeNode(nodeId);
        adminBroadcaster.broadcast("CLUSTER_NODE_RESUMED", "Admin removed cluster node drain marker", Map.of("nodeId", nodeId));
        return response;
    }

    private boolean matchesTrace(AdminEventPayload event, String traceId) {
        if (Objects.equals(traceId, event.eventId()) || Objects.equals(traceId, event.eventType())) {
            return true;
        }
        if (event.data() == null) {
            return false;
        }
        for (Object value : event.data().values()) {
            if (Objects.equals(traceId, String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private void requireKnownNode(String nodeId) {
        if (Objects.equals(nodeId, gatewayProperties.nodeId())) {
            return;
        }
        var known = clusterNodeRegistry.findByNodeId(nodeId).isPresent()
                || clusterRemoteStateRegistry.findByNodeId(nodeId).isPresent()
                || clusterNodeRegistry.list().stream().anyMatch(node -> node.status() == ClusterNodeStatus.SELF && Objects.equals(node.nodeId(), nodeId));
        if (!known) {
            throw new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Cluster node not found: " + nodeId);
        }
    }
}
