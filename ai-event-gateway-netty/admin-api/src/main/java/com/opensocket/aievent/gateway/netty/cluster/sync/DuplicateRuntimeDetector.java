package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.authorization.AgentSecurityEventPublisher;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import com.opensocket.aievent.gateway.netty.authorization.DuplicateRuntimeSecurityEvent;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects duplicate active runtime sessions across local and remote Netty cluster state snapshots.
 * The detector is deliberately advisory: it publishes an observation to Core security-event ingestion,
 * while Core remains responsible for quarantine, credential rotation, and disconnect enforcement.
 */
@Component
public class DuplicateRuntimeDetector {
    private final AgentRegistry agentRegistry;
    private final ClusterRemoteStateRegistry remoteStateRegistry;
    private final AgentSecurityEventPublisher securityEventPublisher;
    private final CoreAgentAuthorizationProperties authorizationProperties;
    private final GatewayProperties gatewayProperties;
    private final Map<String, String> lastPublishedSignatureByAgent = new ConcurrentHashMap<>();

    public DuplicateRuntimeDetector(
            AgentRegistry agentRegistry,
            ClusterRemoteStateRegistry remoteStateRegistry,
            AgentSecurityEventPublisher securityEventPublisher,
            CoreAgentAuthorizationProperties authorizationProperties,
            GatewayProperties gatewayProperties
    ) {
        this.agentRegistry = agentRegistry;
        this.remoteStateRegistry = remoteStateRegistry;
        this.securityEventPublisher = securityEventPublisher == null ? AgentSecurityEventPublisher.noop() : securityEventPublisher;
        this.authorizationProperties = authorizationProperties;
        this.gatewayProperties = gatewayProperties;
    }

    public List<DuplicateRuntimeSecurityEvent> detectAndPublishClusterDuplicates() {
        if (authorizationProperties != null && !authorizationProperties.duplicateRuntimeDetectionEnabled()) {
            return List.of();
        }
        Map<String, List<ObservedRuntime>> byAgent = new LinkedHashMap<>();
        for (var local : agentRegistry.list()) {
            if (active(local.status())) {
                byAgent.computeIfAbsent(local.agentId(), ignored -> new ArrayList<>()).add(ObservedRuntime.from(local.agentId(), local.gatewayNodeId(), local.status() == null ? null : local.status().name(), local.connectionType() == null ? null : local.connectionType().name(), local.connectionId(), local.sessionId(), local.remoteAddress(), local.lastHeartbeatAt() == null ? null : local.lastHeartbeatAt().toString()));
            }
        }
        for (var remote : remoteStateRegistry.list()) {
            for (AgentResponse agent : remote.agents()) {
                if (active(agent.status())) {
                    byAgent.computeIfAbsent(agent.agentId(), ignored -> new ArrayList<>()).add(ObservedRuntime.from(agent.agentId(), firstNonBlank(agent.gatewayNodeId(), remote.nodeId()), agent.status() == null ? null : agent.status().name(), agent.connectionType() == null ? null : agent.connectionType().name(), agent.connectionId(), agent.sessionId(), agent.remoteAddress(), agent.lastHeartbeatAt() == null ? null : agent.lastHeartbeatAt().toString()));
                }
            }
        }

        List<DuplicateRuntimeSecurityEvent> detected = new ArrayList<>();
        for (var entry : byAgent.entrySet()) {
            List<ObservedRuntime> runtimes = deduplicate(entry.getValue());
            Set<String> gatewayNodeIds = new LinkedHashSet<>();
            for (var runtime : runtimes) gatewayNodeIds.add(runtime.gatewayNodeId());
            if (runtimes.size() <= 1 && gatewayNodeIds.size() <= 1) {
                continue;
            }
            String signature = signature(runtimes);
            String previous = lastPublishedSignatureByAgent.put(entry.getKey(), signature);
            if (signature.equals(previous)) {
                continue;
            }
            List<Map<String, Object>> sessions = runtimes.stream().map(ObservedRuntime::toMap).toList();
            var event = new DuplicateRuntimeSecurityEvent(
                    entry.getKey(),
                    gatewayProperties.nodeId(),
                    List.copyOf(gatewayNodeIds),
                    runtimes.size(),
                    sessions,
                    "Duplicate runtime sessions detected from Netty cluster state synchronization.",
                    "NETTY_CLUSTER_STATE_SYNC",
                    false,
                    true,
                    OffsetDateTime.now()
            );
            securityEventPublisher.publishDuplicateRuntime(event);
            detected.add(event);
        }
        return detected;
    }

    private static boolean active(AgentStatus status) {
        return status != AgentStatus.OFFLINE && status != AgentStatus.TIMEOUT && status != AgentStatus.DISCONNECTED;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static List<ObservedRuntime> deduplicate(List<ObservedRuntime> values) {
        Map<String, ObservedRuntime> unique = new LinkedHashMap<>();
        for (ObservedRuntime value : values) {
            unique.put(value.sessionKey(), value);
        }
        return List.copyOf(unique.values());
    }

    private static String signature(List<ObservedRuntime> runtimes) {
        return runtimes.stream().map(ObservedRuntime::sessionKey).sorted().reduce((a, b) -> a + "|" + b).orElse("");
    }

    private record ObservedRuntime(String agentId, String gatewayNodeId, String status, String connectionType, String connectionId, String sessionId, String remoteAddress, String lastHeartbeatAt) {
        static ObservedRuntime from(String agentId, String gatewayNodeId, String status, String connectionType, String connectionId, String sessionId, String remoteAddress, String lastHeartbeatAt) {
            return new ObservedRuntime(agentId, gatewayNodeId == null || gatewayNodeId.isBlank() ? "unknown" : gatewayNodeId, status, connectionType, connectionId, sessionId, remoteAddress, lastHeartbeatAt);
        }
        String sessionKey() {
            return gatewayNodeId + ":" + (sessionId == null || sessionId.isBlank() ? connectionId : sessionId);
        }
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("agentId", agentId);
            map.put("gatewayNodeId", gatewayNodeId);
            map.put("status", status);
            map.put("connectionType", connectionType);
            map.put("connectionId", connectionId);
            map.put("sessionId", sessionId);
            map.put("remoteAddress", remoteAddress);
            map.put("lastHeartbeatAt", lastHeartbeatAt);
            return map;
        }
    }
}
