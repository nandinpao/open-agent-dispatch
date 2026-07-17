package com.opensocket.aievent.gateway.netty.admin.runtime;

import com.opensocket.aievent.gateway.netty.admin.AdminRuntimeMetricsService;
import com.opensocket.aievent.gateway.netty.admin.GatewayStatusService;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeAgentConnectionResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeAgentConnectionsResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeConnectionSummaryResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeCallbackRelayObservabilityResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeDeliveryObservabilityResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeInboundObservabilityResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeSloSnapshotResponse;
import com.opensocket.aievent.gateway.netty.admin.runtime.dto.RuntimeSummaryResponse;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewService;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.authorization.RejectedAgentConnectionSnapshot;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryService;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayMetrics;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventForwarder;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * P6.4 Admin Runtime Observability facade.
 *
 * <p>This controller gives Admin UI a stable transport-runtime view centered on connections,
 * command delivery, and inbound events. It deliberately exposes no task lifecycle or routing
 * decision API.</p>
 */
@RestController
@RequestMapping("/api/admin/runtime")
public class AdminRuntimeController {

    private final GatewayStatusService gatewayStatusService;
    private final AdminRuntimeMetricsService adminRuntimeMetricsService;
    private final TcpConnectionRegistry tcpConnectionRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final AgentRegistry agentRegistry;
    private final CommandDeliveryService commandDeliveryService;
    private final InboundEventForwarder inboundEventForwarder;
    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final TaskCallbackRelayMetrics taskCallbackRelayMetrics;
    private final ClusterOverviewService clusterOverviewService;
    private final AgentProperties agentProperties;

    public AdminRuntimeController(
            GatewayStatusService gatewayStatusService,
            AdminRuntimeMetricsService adminRuntimeMetricsService,
            TcpConnectionRegistry tcpConnectionRegistry,
            WebSocketSessionRegistry webSocketSessionRegistry,
            AgentRegistry agentRegistry,
            CommandDeliveryService commandDeliveryService,
            InboundEventForwarder inboundEventForwarder,
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry,
            TaskCallbackRelayMetrics taskCallbackRelayMetrics,
            ClusterOverviewService clusterOverviewService,
            AgentProperties agentProperties
    ) {
        this.gatewayStatusService = gatewayStatusService;
        this.adminRuntimeMetricsService = adminRuntimeMetricsService;
        this.tcpConnectionRegistry = tcpConnectionRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.agentRegistry = agentRegistry;
        this.commandDeliveryService = commandDeliveryService;
        this.inboundEventForwarder = inboundEventForwarder;
        this.authorizationRuntimeRegistry = authorizationRuntimeRegistry;
        this.taskCallbackRelayMetrics = taskCallbackRelayMetrics;
        this.clusterOverviewService = clusterOverviewService;
        this.agentProperties = agentProperties == null ? new AgentProperties() : agentProperties;
    }

    @GetMapping({"", "/summary", "/snapshot", "/local"})
    public RuntimeSummaryResponse summary() {
        return new RuntimeSummaryResponse(
                gatewayStatusService.getStatus(),
                adminRuntimeMetricsService.snapshot(),
                connections(),
                localAgents(),
                commandDeliveryService.metrics(),
                inboundEventForwarder.metrics(),
                taskCallbackRelayMetrics.snapshot(),
                OffsetDateTime.now()
        );
    }

    @GetMapping("/connections")
    public RuntimeConnectionSummaryResponse connections() {
        return RuntimeConnectionSummaryResponse.from(
                tcpConnectionRegistry.list(),
                webSocketSessionRegistry.list()
        );
    }

    @GetMapping("/agents")
    public RuntimeAgentConnectionsResponse agents(@RequestParam(defaultValue = "local") String scope) {
        if ("cluster".equalsIgnoreCase(scope) || "all".equalsIgnoreCase(scope)) {
            return clusterAgents();
        }
        return localAgents();
    }

    private RuntimeAgentConnectionsResponse localAgents() {
        var agents = agentRegistry.list().stream()
                .map(AgentResponse::from)
                .map(agent -> RuntimeAgentConnectionResponse.from(
                        agent,
                        agentProperties.heartbeatTimeoutSeconds(),
                        authorizationRuntimeRegistry == null ? null : authorizationRuntimeRegistry.findByAgentId(agent.agentId()).orElse(null)))
                .toList();
        return RuntimeAgentConnectionsResponse.from(agents);
    }

    private RuntimeAgentConnectionsResponse clusterAgents() {
        var agents = clusterOverviewService.agents().nodes().stream()
                .flatMap(node -> node.agents().stream()
                        .map(agent -> RuntimeAgentConnectionResponse.from(agent, node.nodeId(), node.self(),
                                node.syncStatus() == null ? null : node.syncStatus().name())))
                .toList();
        return RuntimeAgentConnectionsResponse.from(agents);
    }

    @GetMapping("/rejected-connections")
    public java.util.List<RejectedAgentConnectionSnapshot> rejectedConnections() {
        return authorizationRuntimeRegistry.listRejected();
    }

    @GetMapping("/delivery")
    public RuntimeDeliveryObservabilityResponse delivery(@RequestParam(defaultValue = "100") int limit) {
        return new RuntimeDeliveryObservabilityResponse(
                commandDeliveryService.metrics(),
                commandDeliveryService.history(limit),
                OffsetDateTime.now()
        );
    }

    @GetMapping("/inbound")
    public RuntimeInboundObservabilityResponse inbound(@RequestParam(defaultValue = "100") int limit) {
        return new RuntimeInboundObservabilityResponse(
                inboundEventForwarder.metrics(),
                inboundEventForwarder.history(limit),
                OffsetDateTime.now()
        );
    }
    @GetMapping("/callback-relay")
    public RuntimeCallbackRelayObservabilityResponse callbackRelay(@RequestParam(defaultValue = "100") int limit) {
        return new RuntimeCallbackRelayObservabilityResponse(
                taskCallbackRelayMetrics.snapshot(),
                taskCallbackRelayMetrics.history(limit),
                OffsetDateTime.now()
        );
    }


    @GetMapping("/slo")
    public RuntimeSloSnapshotResponse slo() {
        var delivery = commandDeliveryService.metrics();
        var relay = taskCallbackRelayMetrics.snapshot();
        long deliveryBacklog = Math.max(0L, delivery.activeDeliveries());
        long gatewayRelayBacklog = Math.max(0L, delivery.historySize() - delivery.deliveredAttempts());
        long callbackRelayFailures = relay.failed() + relay.rejected();
        double callbackRelayFailureRatio = ratio(callbackRelayFailures, relay.total());

        java.util.List<java.util.Map<String, Object>> alerts = new java.util.ArrayList<>();
        java.util.Map<String, Object> deliveryBacklogMetric = metric(deliveryBacklog >= 100L ? "CRITICAL" : deliveryBacklog >= 25L ? "WARNING" : "OK", deliveryBacklog, 25L, 100L, "activeDeliveries");
        java.util.Map<String, Object> gatewayRelayBacklogMetric = metric(gatewayRelayBacklog >= 100L ? "CRITICAL" : gatewayRelayBacklog >= 25L ? "WARNING" : "OK", gatewayRelayBacklog, 25L, 100L, "historySizeMinusDelivered");
        java.util.Map<String, Object> callbackRelayMetric = ratioMetric(callbackRelayFailureRatio >= 0.30d ? "CRITICAL" : callbackRelayFailureRatio >= 0.10d ? "WARNING" : "OK", callbackRelayFailureRatio, 0.10d, 0.30d, callbackRelayFailures, relay.total());
        collectRuntimeSloAlert(alerts, "deliveryBacklog", (String) deliveryBacklogMetric.get("status"), "Active deliveries=" + deliveryBacklog);
        collectRuntimeSloAlert(alerts, "gatewayRelayBacklog", (String) gatewayRelayBacklogMetric.get("status"), "Gateway relay backlog=" + gatewayRelayBacklog);
        collectRuntimeSloAlert(alerts, "callbackRelay", (String) callbackRelayMetric.get("status"), "Callback relay failure ratio=" + callbackRelayFailureRatio);
        String status = alerts.stream().anyMatch(alert -> "CRITICAL".equals(alert.get("severity"))) ? "CRITICAL"
                : alerts.stream().anyMatch(alert -> "WARNING".equals(alert.get("severity"))) ? "WARNING" : "OK";
        return new RuntimeSloSnapshotResponse(
                status,
                deliveryBacklogMetric,
                gatewayRelayBacklogMetric,
                callbackRelayMetric,
                alerts,
                OffsetDateTime.now()
        );
    }

    @GetMapping("/stream")
    public RuntimeStreamInfo stream() {
        return new RuntimeStreamInfo(
                "WEBSOCKET",
                "/api/admin/runtime/stream",
                "Connect to the Netty WebSocket port. This path is an alias of the Admin realtime channel and emits AdminRealtimeEvent payloads.",
                java.util.List.of("agent.authorized", "agent.authorization.denied", "agent.disconnected", "delivery.failed", "callback.relay.failed", "gateway.metrics.updated"),
                OffsetDateTime.now()
        );
    }


    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) return 0.0d;
        return Math.round(((double) numerator / (double) denominator) * 10000.0d) / 10000.0d;
    }

    private java.util.Map<String, Object> metric(String status, long value, long warning, long critical, String unit) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("status", status);
        map.put("value", value);
        map.put("warningThreshold", warning);
        map.put("criticalThreshold", critical);
        map.put("unit", unit);
        return map;
    }

    private java.util.Map<String, Object> ratioMetric(String status, double ratio, double warning, double critical, long numerator, long denominator) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("status", status);
        map.put("ratio", ratio);
        map.put("warningRatio", warning);
        map.put("criticalRatio", critical);
        map.put("numerator", numerator);
        map.put("denominator", denominator);
        return map;
    }

    private void collectRuntimeSloAlert(java.util.List<java.util.Map<String, Object>> alerts, String objective, String severity, String message) {
        if ("OK".equals(severity) || severity == null) return;
        java.util.Map<String, Object> alert = new java.util.LinkedHashMap<>();
        alert.put("objective", objective);
        alert.put("severity", severity);
        alert.put("message", message);
        alerts.add(alert);
    }

    public record RuntimeStreamInfo(String transport, String path, String description, java.util.List<String> eventTypes, OffsetDateTime generatedAt) {}

}
