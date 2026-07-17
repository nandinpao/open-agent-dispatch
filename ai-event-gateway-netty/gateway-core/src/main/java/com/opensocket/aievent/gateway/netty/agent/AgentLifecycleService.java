package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentStatusChangePayload;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationDeniedException;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationClient;
import com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationRequest;
import com.opensocket.aievent.gateway.netty.authorization.AgentSecurityEventPublisher;
import com.opensocket.aievent.gateway.netty.authorization.DuplicateRuntimeSecurityEvent;
import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.directory.CoreDirectorySyncPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent lifecycle component for the Netty transport gateway. It maintains only the local connection
 * view needed by Admin UI and command delivery. Task scheduling, agent assignment, and business
 * recovery decisions belong to ai-event-gateway-core / control-plane and are intentionally not
 * triggered from this service.
 */
@Service
public class AgentLifecycleService {

    private final AgentRegistry agentRegistry;
    private final AgentProperties agentProperties;
    private final AdminEventPublisher adminBroadcaster;
    private final CoreDirectorySyncPublisher directorySyncPublisher;
    private final AgentConnectionAuthorizationClient authorizationClient;
    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final AgentSecurityEventPublisher securityEventPublisher;

    @Autowired
    public AgentLifecycleService(
            AgentRegistry agentRegistry,
            AgentProperties agentProperties,
            AdminEventPublisher adminBroadcaster,
            CoreDirectorySyncPublisher directorySyncPublisher,
            AgentConnectionAuthorizationClient authorizationClient,
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry,
            AgentSecurityEventPublisher securityEventPublisher
    ) {
        this.agentRegistry = agentRegistry;
        this.agentProperties = agentProperties;
        this.adminBroadcaster = adminBroadcaster;
        this.directorySyncPublisher = directorySyncPublisher == null ? CoreDirectorySyncPublisher.noop() : directorySyncPublisher;
        this.authorizationClient = authorizationClient == null ? request -> com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationResponse.allow(request.agentId()) : authorizationClient;
        this.authorizationRuntimeRegistry = authorizationRuntimeRegistry;
        this.securityEventPublisher = securityEventPublisher == null ? AgentSecurityEventPublisher.noop() : securityEventPublisher;
    }

    /** Backward-compatible constructor retained for focused protocol and lifecycle unit tests. */
    public AgentLifecycleService(
            AgentRegistry agentRegistry,
            AgentProperties agentProperties,
            AdminEventPublisher adminBroadcaster
    ) {
        this(agentRegistry, agentProperties, adminBroadcaster, CoreDirectorySyncPublisher.noop(), null, null, AgentSecurityEventPublisher.noop());
    }

    /**
     * Registers a logical Agent after a TCP connection or WebSocket session sends AGENT_REGISTER /
     * agent.registered. Registration updates local transport state only; it does not dispatch queued
     * tasks.
     */
    public AgentSnapshot registerAgent(
            AgentRegisterPayload payload,
            ConnectionType connectionType,
            String connectionId,
            String sessionId,
            String remoteAddress
    ) {
        var request = new AgentConnectionAuthorizationRequest(
                payload.agentId(),
                payload.agentType(),
                connectionType,
                null,
                connectionId,
                sessionId,
                remoteAddress,
                payload.capabilities(),
                payload.metadata(),
                firstNonBlank(payload.onboardingToken(), metadataText(payload.metadata(), "credentialToken", "credential", "authToken", "agentToken", "onboardingToken", "token")),
                metadataText(payload.metadata(), "publicKeyFingerprint", "fingerprint")
        );
        if (authorizationRuntimeRegistry != null) {
            authorizationRuntimeRegistry.markUnverified(request);
        }
        var authorization = authorizationClient.authorize(request);
        if (authorization == null || !authorization.allowed()) {
            var rejected = authorizationRuntimeRegistry == null ? null : authorizationRuntimeRegistry.markRejected(request, authorization);
            if (rejected != null) {
                securityEventPublisher.publishRejectedConnection(rejected);
            }
            var reason = authorization == null ? "AUTHORIZATION_DENIED" : authorization.reason();
            adminBroadcaster.broadcast(
                    "AGENT_AUTHORIZATION_DENIED",
                    "Agent connection rejected by Core authorization",
                    Map.of(
                            "agentId", payload.agentId(),
                            "connectionType", connectionType.name(),
                            "connectionId", connectionId == null ? "" : connectionId,
                            "sessionId", sessionId == null ? "" : sessionId,
                            "remoteAddress", remoteAddress == null ? "" : remoteAddress,
                            "reason", reason == null ? "AUTHORIZATION_DENIED" : reason,
                            "rejectedConnectionId", rejected == null ? "" : rejected.rejectedConnectionId()
                    )
            );
            throw new AgentAuthorizationDeniedException("AGENT_AUTHORIZATION_DENIED", reason);
        }
        if (authorizationRuntimeRegistry != null) {
            authorizationRuntimeRegistry.markAuthorized(request, authorization);
        }
        var previous = agentRegistry.findById(payload.agentId());
        var snapshot = agentRegistry.register(payload, connectionType, connectionId, sessionId, remoteAddress);
        publishLocalDuplicateRuntimeIfNeeded(previous.orElse(null), snapshot);
        adminBroadcaster.broadcast(
                "AGENT_AUTHORIZED",
                "Agent authorized by Core and registered on local transport gateway",
                eventData(snapshot)
        );
        directorySyncPublisher.publishAgentConnected(snapshot);
        return snapshot;
    }

    /**
     * Updates lastHeartbeatAt and the reported runtime status for an Agent. IDLE/BUSY is treated as
     * observed transport metadata only; no local scheduling decision is made here.
     */
    public Optional<AgentSnapshot> heartbeat(AgentHeartbeatPayload payload) {
        var snapshot = agentRegistry.heartbeat(payload);
        snapshot.ifPresent(agent -> {
            adminBroadcaster.broadcast(
                    "AGENT_HEARTBEAT",
                    "Agent heartbeat received",
                    eventData(agent)
            );
            directorySyncPublisher.publishAgentHeartbeat(agent);
        });
        return snapshot;
    }

    public Optional<AgentSnapshot> statusChange(AgentStatusChangePayload payload) {
        var snapshot = agentRegistry.changeStatus(payload);
        snapshot.ifPresent(agent -> {
            adminBroadcaster.broadcast(
                    "AGENT_STATUS_CHANGED",
                    payload.reason() == null ? "Agent status changed" : payload.reason(),
                    eventData(agent)
            );
            directorySyncPublisher.publishAgentHeartbeat(agent);
        });
        return snapshot;
    }

    public Optional<AgentSnapshot> markOfflineByTcpConnection(String connectionId) {
        var snapshot = agentRegistry.markOfflineByConnection(ConnectionType.TCP, connectionId);
        snapshot.ifPresent(agent -> {
            if (authorizationRuntimeRegistry != null) {
                authorizationRuntimeRegistry.removeByEndpoint(ConnectionType.TCP, connectionId);
            }
            adminBroadcaster.broadcast(
                    "AGENT_OFFLINE",
                    "Agent TCP connection disconnected",
                    eventData(agent)
            );
            directorySyncPublisher.publishAgentDisconnected(agent, "Agent TCP connection disconnected");
        });
        return snapshot;
    }

    public Optional<AgentSnapshot> markOfflineByWebSocketSession(String sessionId) {
        var snapshot = agentRegistry.markOfflineByConnection(ConnectionType.WEBSOCKET, sessionId);
        snapshot.ifPresent(agent -> {
            if (authorizationRuntimeRegistry != null) {
                authorizationRuntimeRegistry.removeByEndpoint(ConnectionType.WEBSOCKET, sessionId);
            }
            adminBroadcaster.broadcast(
                    "AGENT_OFFLINE",
                    "Agent WebSocket session disconnected",
                    eventData(agent)
            );
            directorySyncPublisher.publishAgentDisconnected(agent, "Agent WebSocket session disconnected");
        });
        return snapshot;
    }

    /**
     * Marks Agents as TIMEOUT when their heartbeat age exceeds the configured threshold. Timeout is a
     * transport observation only; task recovery belongs to the future Core / Control Plane.
     */
    public int markTimeoutAgents() {
        var timeout = Duration.ofSeconds(agentProperties.heartbeatTimeoutSeconds());
        var timedOutAgents = agentRegistry.markTimeouts(timeout);
        for (AgentSnapshot agent : timedOutAgents) {
            adminBroadcaster.broadcast(
                    "AGENT_TIMEOUT",
                    "Agent heartbeat timeout",
                    eventData(agent)
            );
            directorySyncPublisher.publishAgentDisconnected(agent, "Agent heartbeat timeout");
        }
        return timedOutAgents.size();
    }

    /**
     * Converts the internal AgentSnapshot into a compact event payload for Admin WebSocket broadcasts.
     * currentTaskId is retained as observed agent-reported metadata, not a Netty assignment decision.
     */
    public static Map<String, Object> eventData(AgentSnapshot agent) {
        return Map.ofEntries(
                Map.entry("agentId", agent.agentId()),
                Map.entry("agentType", agent.agentType().name()),
                Map.entry("connectionType", agent.connectionType().name()),
                Map.entry("gatewayNodeId", agent.gatewayNodeId()),
                Map.entry("status", agent.status().name()),
                Map.entry("capabilities", agent.capabilities()),
                Map.entry("currentTaskId", agent.currentTaskId() == null ? "" : agent.currentTaskId()),
                Map.entry("remoteAddress", agent.remoteAddress() == null ? "" : agent.remoteAddress()),
                Map.entry("connectionId", agent.connectionId() == null ? "" : agent.connectionId()),
                Map.entry("sessionId", agent.sessionId() == null ? "" : agent.sessionId()),
                Map.entry("metadata", agent.metadata() == null ? Map.of() : agent.metadata())
        );
    }


    private void publishLocalDuplicateRuntimeIfNeeded(AgentSnapshot previous, AgentSnapshot current) {
        if (previous == null || current == null || !sameAgent(previous, current) || !active(previous) || !active(current)) {
            return;
        }
        boolean sameEndpoint = safeEquals(previous.connectionId(), current.connectionId()) && safeEquals(previous.sessionId(), current.sessionId());
        if (sameEndpoint) {
            return;
        }
        Map<String, Object> previousSession = runtimeSession(previous);
        Map<String, Object> currentSession = runtimeSession(current);
        securityEventPublisher.publishDuplicateRuntime(new DuplicateRuntimeSecurityEvent(
                current.agentId(),
                current.gatewayNodeId(),
                List.of(current.gatewayNodeId()),
                2,
                List.of(previousSession, currentSession),
                "Duplicate local runtime session detected during Agent registration. The new registration replaced an already active runtime record on the same Netty node.",
                "NETTY_LOCAL_REGISTER",
                true,
                false,
                OffsetDateTime.now()
        ));
    }

    private static boolean sameAgent(AgentSnapshot left, AgentSnapshot right) {
        return left.agentId() != null && left.agentId().equals(right.agentId());
    }

    private static boolean active(AgentSnapshot snapshot) {
        return snapshot.status() != AgentStatus.OFFLINE
                && snapshot.status() != AgentStatus.TIMEOUT
                && snapshot.status() != AgentStatus.DISCONNECTED;
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static Map<String, Object> runtimeSession(AgentSnapshot snapshot) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("agentId", snapshot.agentId());
        session.put("gatewayNodeId", snapshot.gatewayNodeId());
        session.put("status", snapshot.status() == null ? null : snapshot.status().name());
        session.put("connectionType", snapshot.connectionType() == null ? null : snapshot.connectionType().name());
        session.put("connectionId", snapshot.connectionId());
        session.put("sessionId", snapshot.sessionId());
        session.put("remoteAddress", snapshot.remoteAddress());
        session.put("lastHeartbeatAt", snapshot.lastHeartbeatAt() == null ? null : snapshot.lastHeartbeatAt().toString());
        return session;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String metadataText(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        Object auth = metadata.get("auth");
        if (auth instanceof Map<?, ?> authMap) {
            for (String key : keys) {
                Object value = authMap.get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString().trim();
                }
            }
            Object token = authMap.get("token");
            if (token != null && !token.toString().isBlank() && java.util.Arrays.asList(keys).contains("credentialToken")) {
                return token.toString().trim();
            }
        }
        return null;
    }
}
