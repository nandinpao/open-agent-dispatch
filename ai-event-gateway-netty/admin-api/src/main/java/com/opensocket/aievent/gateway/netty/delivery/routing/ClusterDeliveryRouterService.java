package com.opensocket.aievent.gateway.netty.delivery.routing;

import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterNodeSyncStatus;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterRemoteStateRegistry;
import com.opensocket.aievent.gateway.netty.cluster.sync.RemoteClusterStateSnapshot;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryRequest;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryResponse;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryService;
import com.opensocket.aievent.gateway.netty.delivery.DeliveryStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cluster-aware command delivery router.
 *
 * <p>This service is deliberately one-hop. The public cluster delivery endpoint resolves the
 * owning gateway from local AgentRegistry and cluster state-sync snapshots. Remote forwarding then
 * calls the target node's <code>/internal/delivery/agents/{agentId}/commands</code> endpoint, which
 * remains local-only. This avoids recursive forwarding loops and keeps business task ownership out
 * of ai-event-gateway-netty.</p>
 */
@Service
public class ClusterDeliveryRouterService {

    private final GatewayProperties gatewayProperties;
    private final DeliveryRouterProperties deliveryRouterProperties;
    private final AdminProperties adminProperties;
    private final AgentRegistry agentRegistry;
    private final ClusterRemoteStateRegistry clusterRemoteStateRegistry;
    private final CommandDeliveryService localDeliveryService;
    private final ObjectMapper objectMapper;

    public ClusterDeliveryRouterService(
            GatewayProperties gatewayProperties,
            DeliveryRouterProperties deliveryRouterProperties,
            AdminProperties adminProperties,
            AgentRegistry agentRegistry,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            CommandDeliveryService localDeliveryService,
            ObjectMapper objectMapper
    ) {
        this.gatewayProperties = gatewayProperties;
        this.deliveryRouterProperties = deliveryRouterProperties;
        this.adminProperties = adminProperties;
        this.agentRegistry = agentRegistry;
        this.clusterRemoteStateRegistry = clusterRemoteStateRegistry;
        this.localDeliveryService = localDeliveryService;
        this.objectMapper = objectMapper;
    }

    public CommandDeliveryResponse deliver(String agentId, CommandDeliveryRequest request) {
        if (blank(agentId)) {
            return failure(agentId, DeliveryStatus.INVALID_COMMAND, "agentId is required");
        }

        var localAgent = agentRegistry.findById(agentId).filter(this::connected).orElse(null);
        if (localAgent != null && deliveryRouterProperties.safePreferLocal()) {
            return localDeliveryService.deliverToAgent(agentId, request);
        }

        if (!deliveryRouterProperties.enabled()) {
            return localDeliveryService.deliverToAgent(agentId, request);
        }

        var remoteCandidates = remoteCandidates(agentId);
        if (localAgent != null && remoteCandidates.isEmpty()) {
            return localDeliveryService.deliverToAgent(agentId, request);
        }
        if (localAgent != null && !deliveryRouterProperties.safePreferLocal()) {
            remoteCandidates.add(0, RemoteAgentRoute.local(localAgent, gatewayProperties.nodeId()));
        }

        if (remoteCandidates.isEmpty()) {
            return failure(agentId, DeliveryStatus.AGENT_NOT_CONNECTED,
                    "Agent is not connected to any synced gateway node");
        }
        if (deliveryRouterProperties.safeRejectDuplicateAgents() && remoteCandidates.size() > 1) {
            return failure(agentId, DeliveryStatus.INVALID_COMMAND,
                    "Duplicate connected Agent detected across gateway nodes: " + candidateNodeIds(remoteCandidates));
        }

        var target = remoteCandidates.stream()
                .filter(candidate -> !candidate.local())
                .findFirst()
                .orElse(remoteCandidates.get(0));

        if (target.local()) {
            return localDeliveryService.deliverToAgent(agentId, request);
        }
        return forwardToRemote(agentId, request, target);
    }

    public DeliveryRouteDecision routeDecision(String agentId) {
        var candidates = remoteCandidates(agentId);
        agentRegistry.findById(agentId).filter(this::connected)
                .ifPresent(local -> candidates.add(0, RemoteAgentRoute.local(local, gatewayProperties.nodeId())));
        var target = candidates.isEmpty() ? null : candidates.get(0);
        return new DeliveryRouteDecision(
                agentId,
                gatewayProperties.nodeId(),
                target == null ? null : target.nodeId(),
                target == null || target.local() ? null : target.adminEndpoint(),
                target == null ? "NOT_FOUND" : target.local() ? "LOCAL" : "REMOTE",
                target == null ? "No connected Agent was found in local registry or synced remote states" : "Resolved from runtime Agent registry",
                candidateNodeIds(candidates),
                OffsetDateTime.now()
        );
    }

    private CommandDeliveryResponse forwardToRemote(String agentId, CommandDeliveryRequest request, RemoteAgentRoute target) {
        if (blank(target.adminEndpoint())) {
            return failure(agentId, DeliveryStatus.DELIVERY_FAILED,
                    "Target gateway has no reachable Admin endpoint: " + target.nodeId());
        }
        try {
            var endpoint = target.adminEndpoint() + "/internal/delivery/agents/" + encodePathSegment(agentId) + "/commands";
            var timeout = Duration.ofMillis(deliveryRouterProperties.safeRequestTimeoutMs());
            var body = objectMapper.writeValueAsString(request == null ? new CommandDeliveryRequest(null, null, Map.of(), null, "cluster-router") : request);
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Routed-By-Gateway-Node", gatewayProperties.nodeId());
            if (!blank(adminProperties.internalToken())) {
                requestBuilder.header("Authorization", "Bearer " + adminProperties.internalToken());
                requestBuilder.header("X-Cluster-Token", adminProperties.internalToken());
            }
            var client = HttpClient.newBuilder().connectTimeout(timeout).build();
            var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failure(agentId, DeliveryStatus.DELIVERY_FAILED,
                        "Remote gateway " + target.nodeId() + " returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), CommandDeliveryResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(agentId, DeliveryStatus.DELIVERY_FAILED,
                    "Interrupted while forwarding command to remote gateway " + target.nodeId());
        } catch (Exception e) {
            return failure(agentId, DeliveryStatus.DELIVERY_FAILED,
                    "Failed to forward command to remote gateway " + target.nodeId() + ": " + e.getMessage());
        }
    }

    private List<RemoteAgentRoute> remoteCandidates(String agentId) {
        var candidates = new ArrayList<RemoteAgentRoute>();
        for (var state : clusterRemoteStateRegistry.list()) {
            if (deliveryRouterProperties.safeRequireSyncedRemoteState() && state.syncStatus() != ClusterNodeSyncStatus.SYNCED) {
                continue;
            }
            if (state.agents() == null || state.node() == null || blank(state.node().host()) || state.node().adminPort() <= 0) {
                continue;
            }
            for (var agent : state.agents()) {
                if (agent == null || !agentId.equals(agent.agentId()) || !connected(agent)) {
                    continue;
                }
                candidates.add(RemoteAgentRoute.remote(
                        agent,
                        state,
                        "http://" + state.node().host() + ":" + state.node().adminPort()
                ));
            }
        }
        candidates.sort(this::compareRoutesByHeartbeatDesc);
        return candidates;
    }

    private int compareRoutesByHeartbeatDesc(RemoteAgentRoute left, RemoteAgentRoute right) {
        var leftHeartbeat = left.lastHeartbeatAt();
        var rightHeartbeat = right.lastHeartbeatAt();
        if (leftHeartbeat == null && rightHeartbeat != null) {
            return 1;
        }
        if (leftHeartbeat != null && rightHeartbeat == null) {
            return -1;
        }
        if (leftHeartbeat != null) {
            var heartbeatCompare = rightHeartbeat.compareTo(leftHeartbeat);
            if (heartbeatCompare != 0) {
                return heartbeatCompare;
            }
        }
        return left.nodeId().compareTo(right.nodeId());
    }

    private boolean connected(AgentSnapshot agent) {
        if (agent == null || agent.status() == AgentStatus.OFFLINE || agent.status() == AgentStatus.TIMEOUT) {
            return false;
        }
        if (agent.connectionType() == ConnectionType.TCP) {
            return !blank(agent.connectionId());
        }
        if (agent.connectionType() == ConnectionType.WEBSOCKET) {
            return !blank(agent.sessionId());
        }
        return false;
    }

    private boolean connected(AgentResponse agent) {
        if (agent == null || agent.status() == AgentStatus.OFFLINE || agent.status() == AgentStatus.TIMEOUT) {
            return false;
        }
        if (agent.connectionType() == ConnectionType.TCP) {
            return !blank(agent.connectionId());
        }
        if (agent.connectionType() == ConnectionType.WEBSOCKET) {
            return !blank(agent.sessionId());
        }
        return false;
    }

    private CommandDeliveryResponse failure(String agentId, DeliveryStatus status, String message) {
        var now = OffsetDateTime.now();
        return new CommandDeliveryResponse(
                "route-" + UUID.randomUUID(),
                "route-failed-" + UUID.randomUUID(),
                null,
                agentId,
                gatewayProperties.nodeId(),
                status,
                null,
                now,
                now,
                null,
                0,
                message == null ? "" : message,
                null,
                null,
                null,
                null
        );
    }

    private List<String> candidateNodeIds(List<RemoteAgentRoute> candidates) {
        return candidates.stream().map(RemoteAgentRoute::nodeId).distinct().toList();
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record RemoteAgentRoute(
            String nodeId,
            boolean local,
            String adminEndpoint,
            OffsetDateTime lastHeartbeatAt
    ) {
        private static RemoteAgentRoute local(AgentSnapshot agent, String nodeId) {
            return new RemoteAgentRoute(nodeId, true, null, agent.lastHeartbeatAt());
        }

        private static RemoteAgentRoute remote(AgentResponse agent, RemoteClusterStateSnapshot state, String adminEndpoint) {
            return new RemoteAgentRoute(state.nodeId(), false, adminEndpoint, agent.lastHeartbeatAt());
        }
    }
}
