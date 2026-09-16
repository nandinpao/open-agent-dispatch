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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Agent lifecycle component for the Netty transport gateway. It maintains only the local connection
 * view needed by Admin UI and command delivery. Task scheduling, agent assignment, and business
 * recovery decisions belong to ai-event-gateway-core / control-plane and are intentionally not
 * triggered from this service.
 */
@Service
public class AgentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleService.class);

    private final AgentRegistry agentRegistry;
    private final AgentProperties agentProperties;
    private final AdminEventPublisher adminBroadcaster;
    private final CoreDirectorySyncPublisher directorySyncPublisher;
    private final AgentConnectionAuthorizationClient authorizationClient;
    private static final long PENDING_REAUTHORIZATION_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15);

    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final AgentSecurityEventPublisher securityEventPublisher;
    private final Map<String, Long> pendingReauthorizationAttempts = new ConcurrentHashMap<>();

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
        String connectionAttemptId = "acon-" + UUID.randomUUID();
        // New contract: the shared transport onboarding token is validated by the Gateway only.
        // Core must receive the Agent-specific runtime credential when one is supplied.
        // Only explicit LEGACY_SINGLE_TOKEN launchers may promote the transport admission token
        // to a Core credential; DISCOVERY_ONLY and PER_AGENT remain strictly separated.
        String credentialMode = safeText(metadataText(payload.metadata(), "credentialMode")).toUpperCase(Locale.ROOT);
        String explicitRuntimeCredential = metadataText(payload.metadata(), "credentialToken", "credential", "authToken", "agentToken");
        String runtimeCredential = switch (credentialMode) {
            case "PER_AGENT" -> explicitRuntimeCredential;
            case "DISCOVERY_ONLY" -> null;
            case "LEGACY_SINGLE_TOKEN" -> firstNonBlank(
                    explicitRuntimeCredential,
                    payload.onboardingToken(),
                    metadataText(payload.metadata(), "onboardingToken", "token")
            );
            default -> firstNonBlank(
                    explicitRuntimeCredential,
                    payload.onboardingToken(),
                    metadataText(payload.metadata(), "onboardingToken", "token")
            );
        };
        var authorizationMetadata = new LinkedHashMap<String, Object>();
        if (payload.metadata() != null) {
            authorizationMetadata.putAll(payload.metadata());
        }
        authorizationMetadata.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        authorizationMetadata.put("gatewayConnectionAttemptId", connectionAttemptId);
        authorizationMetadata.put("gatewayCredentialMode", credentialMode);
        var request = new AgentConnectionAuthorizationRequest(
                payload.agentId(),
                payload.agentType(),
                connectionType,
                null,
                connectionId,
                sessionId,
                remoteAddress,
                payload.capabilities(),
                Map.copyOf(authorizationMetadata),
                runtimeCredential,
                metadataText(payload.metadata(), "publicKeyFingerprint", "fingerprint")
        );
        String startupRunId = metadataText(payload.metadata(), "startupRunId", "agentStartupId");
        String credentialFingerprint = metadataText(payload.metadata(), "credentialFingerprint");
        log.info("agent_auth_journey stage=CORE_AUTH_REQUESTED startupRunId={} connectionAttemptId={} agentId={} connectionType={} connectionId={} sessionId={} remoteAddress={} credentialPresent={} credentialMode={} credentialFingerprint={}",
                safeText(startupRunId), connectionAttemptId, safeText(payload.agentId()), connectionType == null ? "" : connectionType.name(),
                safeText(connectionId), safeText(sessionId), safeText(remoteAddress),
                runtimeCredential != null && !runtimeCredential.isBlank(), credentialMode, safeText(credentialFingerprint));
        if (authorizationRuntimeRegistry != null) {
            authorizationRuntimeRegistry.markUnverified(request);
        }
        var authorization = authorizationClient.authorize(request);
        log.info("agent_auth_journey stage=CORE_AUTH_RESULT startupRunId={} connectionAttemptId={} agentId={} allowed={} reason={} tenantId={} approvalStatus={} enabled={}",
                safeText(startupRunId), connectionAttemptId, safeText(payload.agentId()), authorization != null && authorization.allowed(),
                authorization == null ? "AUTHORIZATION_DENIED" : safeText(authorization.reason()),
                authorization == null ? "" : safeText(authorization.tenantId()),
                authorization == null ? "" : safeText(authorization.approvalStatus()),
                authorization == null ? null : authorization.enabled());
        if (authorization == null || !authorization.allowed()) {
            var reason = authorization == null ? "AUTHORIZATION_DENIED" : authorization.reason();
            if (isPendingGovernance(reason)) {
                // Discovery-first lifecycle: an unknown / not-yet-approved Agent may remain connected
                // to the local transport runtime so Admin UI can observe it and create governance later.
                // It is intentionally NOT placed in authorizationRuntimeRegistry.authorizedByAgentId,
                // therefore command delivery remains fail-closed until Core approval + credential
                // validation succeeds on a subsequent registration/reconnect.
                var previous = agentRegistry.findById(payload.agentId());
                var snapshot = agentRegistry.register(payload, connectionType, connectionId, sessionId, remoteAddress);
                publishLocalDuplicateRuntimeIfNeeded(previous.orElse(null), snapshot);
                var data = new LinkedHashMap<String, Object>(eventData(snapshot));
                data.put("authorizationState", "PENDING_GOVERNANCE");
                data.put("authorizationReason", reason == null ? "AGENT_NOT_APPROVED" : reason);
                data.put("workloadAuthorized", false);
                data.put("connectionAttemptId", connectionAttemptId);
                adminBroadcaster.broadcast(
                        "AGENT_RUNTIME_OBSERVED",
                        "Agent transport connected and is waiting for Core governance approval",
                        Map.copyOf(data)
                );
                log.info("agent_auth_journey stage=PENDING_GOVERNANCE startupRunId={} connectionAttemptId={} agentId={} reason={} workloadAuthorized=false",
                        safeText(startupRunId), connectionAttemptId, safeText(payload.agentId()), safeText(reason));
                return snapshot;
            }
            var rejected = authorizationRuntimeRegistry == null ? null : authorizationRuntimeRegistry.markRejected(request, authorization);
            if (rejected != null) {
                securityEventPublisher.publishRejectedConnection(rejected);
            }
            log.warn("agent_auth_journey stage=AUTHORIZATION_DENIED connectionAttemptId={} agentId={} reason={} rejectedConnectionId={}",
                    connectionAttemptId, safeText(payload.agentId()), safeText(reason),
                    rejected == null ? "" : safeText(rejected.rejectedConnectionId()));
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
                            "rejectedConnectionId", rejected == null ? "" : rejected.rejectedConnectionId(),
                            "connectionAttemptId", connectionAttemptId
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
        var authorizedData = new LinkedHashMap<String, Object>(eventData(snapshot));
        authorizedData.put("connectionAttemptId", connectionAttemptId);
        authorizedData.put("tenantId", authorization == null ? "" : safeText(authorization.tenantId()));
        authorizedData.put("authorizationState", "AUTHORIZED");
        authorizedData.put("workloadAuthorized", true);
        adminBroadcaster.broadcast(
                "AGENT_AUTHORIZED",
                "Agent authorized by Core and registered on local transport gateway",
                Map.copyOf(authorizedData)
        );
        log.info("agent_auth_journey stage=RUNTIME_AUTHORIZED startupRunId={} connectionAttemptId={} agentId={} tenantId={} workloadAuthorized=true directorySync=CONNECTED_PUBLISH",
                safeText(startupRunId), connectionAttemptId, safeText(payload.agentId()), authorization == null ? "" : safeText(authorization.tenantId()));
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
            if (isWorkloadAuthorized(agent)) {
                directorySyncPublisher.publishAgentHeartbeat(agent);
            } else {
                tryReauthorizePendingAgent(agent);
            }
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
            if (isWorkloadAuthorized(agent)) {
                directorySyncPublisher.publishAgentHeartbeat(agent);
            }
        });
        return snapshot;
    }

    public Optional<AgentSnapshot> markOfflineByTcpConnection(String connectionId) {
        var snapshot = agentRegistry.markOfflineByConnection(ConnectionType.TCP, connectionId);
        snapshot.ifPresent(agent -> {
            boolean coreAuthorized = isWorkloadAuthorized(agent);
            if (authorizationRuntimeRegistry != null) {
                authorizationRuntimeRegistry.removeByEndpoint(ConnectionType.TCP, connectionId);
            }
            pendingReauthorizationAttempts.remove(endpointKey(agent));
            adminBroadcaster.broadcast(
                    "AGENT_OFFLINE",
                    "Agent TCP connection disconnected",
                    eventData(agent)
            );
            if (coreAuthorized) {
                directorySyncPublisher.publishAgentDisconnected(agent, "Agent TCP connection disconnected");
            }
        });
        return snapshot;
    }

    public Optional<AgentSnapshot> markOfflineByWebSocketSession(String sessionId) {
        var snapshot = agentRegistry.markOfflineByConnection(ConnectionType.WEBSOCKET, sessionId);
        snapshot.ifPresent(agent -> {
            boolean coreAuthorized = isWorkloadAuthorized(agent);
            if (authorizationRuntimeRegistry != null) {
                authorizationRuntimeRegistry.removeByEndpoint(ConnectionType.WEBSOCKET, sessionId);
            }
            pendingReauthorizationAttempts.remove(endpointKey(agent));
            adminBroadcaster.broadcast(
                    "AGENT_OFFLINE",
                    "Agent WebSocket session disconnected",
                    eventData(agent)
            );
            if (coreAuthorized) {
                directorySyncPublisher.publishAgentDisconnected(agent, "Agent WebSocket session disconnected");
            }
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
            if (isWorkloadAuthorized(agent)) {
                directorySyncPublisher.publishAgentDisconnected(agent, "Agent heartbeat timeout");
            }
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



    /**
     * A pending-governance response is not a workload authorization. It only permits local
     * transport observation so an administrator can see the Agent and assign Tenant / organization
     * ownership / Source System policy before the Agent becomes dispatch-eligible.
     */
    private static boolean isPendingGovernance(String reason) {
        return reason != null && "AGENT_NOT_APPROVED".equalsIgnoreCase(reason.trim());
    }

    private boolean isWorkloadAuthorized(AgentSnapshot agent) {
        if (agent == null) {
            return false;
        }
        if (authorizationRuntimeRegistry == null) {
            return true;
        }
        return authorizationRuntimeRegistry.isAuthorized(
                agent.connectionType(),
                agent.connectionId(),
                agent.sessionId(),
                agent.agentId()
        );
    }

    /**
     * Re-evaluates a pending Agent only when its original registration carried Agent-specific
     * credential material. DISCOVERY_ONLY registrations intentionally carry no Core credential;
     * after Human approval they must be provisioned with the issued per-Agent credential and
     * reconnect. This prevents the shared Gateway admission secret from becoming workload authority.
     */
    private void tryReauthorizePendingAgent(AgentSnapshot agent) {
        if (agent == null || authorizationRuntimeRegistry == null) {
            return;
        }
        var pending = authorizationRuntimeRegistry.findUnverified(
                agent.connectionType(), agent.connectionId(), agent.sessionId()).orElse(null);
        if (pending == null || !sameAgentId(agent.agentId(), pending.agentId())) {
            return;
        }
        if ((pending.credentialToken() == null || pending.credentialToken().isBlank())
                && (pending.publicKeyFingerprint() == null || pending.publicKeyFingerprint().isBlank())) {
            return;
        }
        String key = endpointKey(agent);
        long now = System.nanoTime();
        Long previous = pendingReauthorizationAttempts.putIfAbsent(key, now);
        if (previous != null) {
            if (now - previous < PENDING_REAUTHORIZATION_INTERVAL_NANOS) {
                return;
            }
            if (!pendingReauthorizationAttempts.replace(key, previous, now)) {
                return;
            }
        }

        var authorization = authorizationClient.authorize(pending);
        if (!governedAllow(authorization)) {
            return;
        }

        var context = authorizationRuntimeRegistry.markAuthorized(pending, authorization);
        if (!authorizationRuntimeRegistry.isAuthorizedAgent(context)) {
            return;
        }
        pendingReauthorizationAttempts.remove(key);
        var data = new LinkedHashMap<String, Object>(eventData(agent));
        data.put("tenantId", context.tenantId());
        data.put("authorizationState", "AUTHORIZED");
        data.put("authorizationReason", "GOVERNANCE_RECONCILED");
        data.put("workloadAuthorized", true);
        adminBroadcaster.broadcast(
                "AGENT_AUTHORIZATION_RECONCILED",
                "Agent governance approval became effective without requiring a transport restart",
                Map.copyOf(data)
        );
        directorySyncPublisher.publishAgentConnected(agent);
        directorySyncPublisher.publishAgentHeartbeat(agent);
    }

    private static boolean governedAllow(com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationResponse authorization) {
        return authorization != null
                && authorization.allowed()
                && authorization.tenantId() != null
                && !authorization.tenantId().isBlank()
                && "APPROVED".equalsIgnoreCase(authorization.approvalStatus())
                && Boolean.TRUE.equals(authorization.enabled());
    }

    private static String endpointKey(AgentSnapshot agent) {
        if (agent == null || agent.connectionType() == null) {
            return "UNKNOWN:";
        }
        return agent.connectionType().name() + ":" + (agent.connectionType() == ConnectionType.TCP
                ? safeText(agent.connectionId()) : safeText(agent.sessionId()));
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static boolean sameAgentId(String left, String right) {
        return left != null && left.equals(right);
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
