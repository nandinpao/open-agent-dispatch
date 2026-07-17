package com.opensocket.aievent.core.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.gateway.GatewayDirectoryService;
import com.opensocket.aievent.core.gateway.GatewayNode;
import com.opensocket.aievent.core.gateway.GatewayNodeQuery;
import com.opensocket.aievent.core.gateway.GatewayNodeStatus;

@RestController
public class GatewayNodeController {
    private final GatewayDirectoryService gatewayDirectoryService;
    private final AgentDirectoryService agentDirectoryService;

    public GatewayNodeController(GatewayDirectoryService gatewayDirectoryService, AgentDirectoryService agentDirectoryService) {
        this.gatewayDirectoryService = gatewayDirectoryService;
        this.agentDirectoryService = agentDirectoryService;
    }

    @PostMapping("/internal/gateway-nodes/register")
    public GatewayNode register(@RequestBody GatewayNode node) {
        return gatewayDirectoryService.register(node);
    }

    @PostMapping("/internal/gateway-nodes/{gatewayNodeId}/heartbeat")
    public GatewayNode heartbeat(@PathVariable String gatewayNodeId, @RequestBody GatewayHeartbeatRequest request) {
        Duration lease = request.leaseTtlSeconds() == null ? null : Duration.ofSeconds(request.leaseTtlSeconds());
        return gatewayDirectoryService.heartbeat(gatewayNodeId, request.status(), lease);
    }

    @PostMapping("/internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/connected")
    public AgentSnapshot agentConnected(@PathVariable String gatewayNodeId,
                                        @PathVariable String agentId,
                                        @RequestBody AgentSnapshot request) {
        return agentDirectoryService.connected(gatewayNodeId, agentId, request == null ? new AgentSnapshot() : request);
    }

    @PostMapping("/internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/heartbeat")
    public AgentSnapshot agentHeartbeat(@PathVariable String gatewayNodeId,
                                        @PathVariable String agentId,
                                        @RequestBody(required = false) GatewayAgentHeartbeatRequest request) {
        GatewayAgentHeartbeatRequest body = request == null
                ? new GatewayAgentHeartbeatRequest(AgentStatus.IDLE, 0, 100, null, Map.of(), null, Map.of(), Map.of())
                : request;
        return agentDirectoryService.gatewayHeartbeat(gatewayNodeId, agentId,
                body.status() == null ? AgentStatus.IDLE : body.status(),
                body.currentTaskCount(),
                body.healthScore() <= 0 ? 100 : body.healthScore(),
                body.agentSessionId(),
                body.runtimeLoad(),
                body.capabilityRevision(),
                body.plugin(),
                body.capabilityProfile());
    }

    @PostMapping("/internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/disconnected")
    public AgentSnapshot agentDisconnected(@PathVariable String gatewayNodeId,
                                           @PathVariable String agentId,
                                           @RequestBody(required = false) GatewayAgentDisconnectedRequest request) {
        GatewayAgentDisconnectedRequest body = request == null ? new GatewayAgentDisconnectedRequest(null, null) : request;
        return agentDirectoryService.disconnected(gatewayNodeId, agentId, body.agentSessionId(), body.reason());
    }

    @PostMapping("/internal/gateway-nodes/{gatewayNodeId}/agents/snapshot")
    public GatewayAgentSnapshotResponse replaceAgentSnapshot(@PathVariable String gatewayNodeId,
                                                             @RequestBody GatewayAgentSnapshotRequest request) {
        List<AgentSnapshot> agents = agentDirectoryService.replaceGatewaySnapshot(gatewayNodeId, request == null ? List.of() : request.agents());
        return new GatewayAgentSnapshotResponse(gatewayNodeId, agents.size(), agents, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @GetMapping("/api/gateway-nodes")
    public List<GatewayNode> gatewayNodes(@RequestParam(required = false) GatewayNodeStatus status,
                                          @RequestParam(required = false) String siteId,
                                          @RequestParam(required = false) String region,
                                          @RequestParam(required = false) String zone,
                                          @RequestParam(defaultValue = "100") int limit) {
        GatewayNodeQuery query = new GatewayNodeQuery();
        query.setStatus(status);
        query.setSiteId(siteId);
        query.setRegion(region);
        query.setZone(zone);
        query.setLimit(limit);
        return gatewayDirectoryService.search(query);
    }

    @GetMapping("/api/gateway-nodes/{gatewayNodeId}")
    public GatewayNode gatewayNode(@PathVariable String gatewayNodeId) {
        return gatewayDirectoryService.get(gatewayNodeId);
    }

    @GetMapping("/api/gateway-nodes/{gatewayNodeId}/agents")
    public List<AgentSnapshot> gatewayAgents(@PathVariable String gatewayNodeId,
                                             @RequestParam(defaultValue = "false") boolean assignableOnly,
                                             @RequestParam(defaultValue = "100") int limit) {
        AgentQuery query = new AgentQuery();
        query.setOwnerGatewayNodeId(gatewayNodeId);
        query.setAssignableOnly(assignableOnly);
        query.setLimit(limit);
        return agentDirectoryService.search(query);
    }

    @GetMapping("/api/gateway-nodes/metadata")
    public GatewayNodeMetadata metadata() {
        return new GatewayNodeMetadata(gatewayDirectoryService.mode(), GatewayNodeStatus.values(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public record GatewayHeartbeatRequest(GatewayNodeStatus status, Long leaseTtlSeconds) {}
    public record GatewayAgentHeartbeatRequest(AgentStatus status, int currentTaskCount, int healthScore, String agentSessionId, Map<String, Object> runtimeLoad, String capabilityRevision, Map<String, Object> plugin, Map<String, Object> capabilityProfile) {}
    public record GatewayAgentDisconnectedRequest(String agentSessionId, String reason) {}
    public record GatewayAgentSnapshotRequest(List<AgentSnapshot> agents) {}
    public record GatewayAgentSnapshotResponse(String gatewayNodeId, int agentCount, List<AgentSnapshot> agents, OffsetDateTime processedAt) {}
    public record GatewayNodeMetadata(String storeMode, GatewayNodeStatus[] statuses, OffsetDateTime now) {}
}
