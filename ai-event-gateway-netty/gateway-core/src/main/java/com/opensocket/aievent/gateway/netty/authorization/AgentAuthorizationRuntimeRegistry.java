package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentAuthorizationRuntimeRegistry {
    private final CoreAgentAuthorizationProperties properties;
    private final Map<String, AgentAuthorizationContext> authorizedByAgentId = new ConcurrentHashMap<>();
    private final Map<String, AgentConnectionAuthorizationRequest> unverifiedByEndpoint = new ConcurrentHashMap<>();
    private final Map<String, RejectedAgentConnectionSnapshot> rejectedById = new ConcurrentHashMap<>();

    public AgentAuthorizationRuntimeRegistry(CoreAgentAuthorizationProperties properties) {
        this.properties = properties;
    }

    public void markUnverified(AgentConnectionAuthorizationRequest request) {
        if (request == null) return;
        unverifiedByEndpoint.put(endpointKey(request.connectionType(), request.connectionId(), request.sessionId()), request);
    }

    public AgentAuthorizationContext markAuthorized(
            AgentConnectionAuthorizationRequest request,
            AgentConnectionAuthorizationResponse response
    ) {
        var context = AgentAuthorizationContext.from(response, request);
        authorizedByAgentId.put(context.agentId(), context);
        unverifiedByEndpoint.remove(endpointKey(request.connectionType(), request.connectionId(), request.sessionId()));
        return context;
    }

    public RejectedAgentConnectionSnapshot markRejected(
            AgentConnectionAuthorizationRequest request,
            AgentConnectionAuthorizationResponse response
    ) {
        unverifiedByEndpoint.remove(endpointKey(request.connectionType(), request.connectionId(), request.sessionId()));
        var rejected = new RejectedAgentConnectionSnapshot(
                "rej-" + System.nanoTime(),
                request.agentId(),
                request.connectionType(),
                request.connectionId(),
                request.sessionId(),
                request.remoteAddress(),
                response == null ? "AUTHORIZATION_DENIED" : response.reason(),
                request.claimedCapabilities(),
                sanitize(request.metadata()),
                OffsetDateTime.now()
        );
        rejectedById.put(rejected.rejectedConnectionId(), rejected);
        trimRejectedHistory();
        return rejected;
    }

    public boolean isAuthorized(ConnectionType connectionType, String connectionId, String sessionId, String agentId) {
        if (agentId == null || agentId.isBlank()) return false;
        var context = authorizedByAgentId.get(agentId);
        if (context == null) return false;
        if (connectionType == ConnectionType.TCP) {
            return connectionId != null && connectionId.equals(context.connectionId());
        }
        if (connectionType == ConnectionType.WEBSOCKET) {
            return sessionId != null && sessionId.equals(context.sessionId());
        }
        return false;
    }

    public boolean isAuthorizedAgent(AgentAuthorizationContext context) {
        return context != null && context.enabled() && "APPROVED".equalsIgnoreCase(context.approvalStatus());
    }

    public Optional<AgentAuthorizationContext> findByAgentId(String agentId) {
        return Optional.ofNullable(authorizedByAgentId.get(agentId));
    }

    public void removeByEndpoint(ConnectionType connectionType, String endpointId) {
        authorizedByAgentId.entrySet().removeIf(entry -> {
            var context = entry.getValue();
            if (connectionType == ConnectionType.TCP) {
                return endpointId != null && endpointId.equals(context.connectionId());
            }
            if (connectionType == ConnectionType.WEBSOCKET) {
                return endpointId != null && endpointId.equals(context.sessionId());
            }
            return false;
        });
        unverifiedByEndpoint.remove(endpointKey(connectionType, connectionType == ConnectionType.TCP ? endpointId : null, connectionType == ConnectionType.WEBSOCKET ? endpointId : null));
    }

    public List<RejectedAgentConnectionSnapshot> listRejected() {
        return rejectedById.values().stream()
                .sorted(Comparator.comparing(RejectedAgentConnectionSnapshot::rejectedAt).reversed())
                .toList();
    }

    public long rejectedCount() {
        return rejectedById.size();
    }

    private void trimRejectedHistory() {
        var limit = properties.rejectedHistoryLimit();
        if (rejectedById.size() <= limit) return;
        var ordered = new ArrayList<>(rejectedById.values());
        ordered.sort(Comparator.comparing(RejectedAgentConnectionSnapshot::rejectedAt).reversed());
        for (var stale : ordered.subList(limit, ordered.size())) {
            rejectedById.remove(stale.rejectedConnectionId());
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, Object>();
        for (var entry : metadata.entrySet()) {
            if (entry.getKey() == null) continue;
            var key = entry.getKey().toLowerCase();
            if (key.contains("token") || key.contains("secret") || key.contains("password") || key.contains("credential")) continue;
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    private String endpointKey(ConnectionType connectionType, String connectionId, String sessionId) {
        return (connectionType == null ? "UNKNOWN" : connectionType.name()) + ":" + (connectionType == ConnectionType.TCP ? connectionId : sessionId);
    }
}
