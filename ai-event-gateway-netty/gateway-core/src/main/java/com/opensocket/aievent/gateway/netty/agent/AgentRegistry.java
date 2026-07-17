package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentStatusChangePayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local Agent connection registry for the Netty transport gateway. It is an in-memory runtime view
 * of Agents connected to this gateway node. It is not a Global Agent Directory and does not perform
 * task assignment or routing decisions.
 */
@Component
public class AgentRegistry {

    private final GatewayProperties gatewayProperties;
    private final Map<String, AgentRecord> agents = new ConcurrentHashMap<>();

    public AgentRegistry(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    /**
     * Creates or replaces the current runtime view of an Agent. The registry is intentionally
     * in-memory because it mirrors local transport connections only. Durable Global Agent Directory,
     * capacity reservation, and task assignment state are owned by ai-event-gateway-core.
     */
    public AgentSnapshot register(
            AgentRegisterPayload payload,
            ConnectionType connectionType,
            String connectionId,
            String sessionId,
            String remoteAddress
    ) {
        var now = OffsetDateTime.now();
        var record = new AgentRecord(
                payload.agentId(),
                payload.agentType() == null ? AgentType.CUSTOM : payload.agentType(),
                connectionType,
                gatewayProperties.nodeId(),
                AgentStatus.IDLE,
                payload.capabilities() == null ? List.of() : List.copyOf(payload.capabilities()),
                null,
                now,
                now,
                now,
                remoteAddress,
                connectionId,
                sessionId,
                initialMetadata(payload)
        );
        agents.put(payload.agentId(), record);
        return record.toSnapshot();
    }


    private Map<String, Object> initialMetadata(AgentRegisterPayload payload) {
        var metadata = new java.util.LinkedHashMap<String, Object>(sanitizedMetadata(payload.metadata()));
        if (payload.capabilityProfile() != null && !payload.capabilityProfile().isEmpty()) {
            metadata.put("capabilityProfile", payload.capabilityProfile());
            Object revision = payload.capabilityProfile().get("revision");
            if (revision != null && !revision.toString().isBlank()) {
                metadata.put("capabilityRevision", revision.toString());
            }
            Object maxConcurrentTasks = payload.capabilityProfile().get("maxConcurrentTasks");
            if (maxConcurrentTasks != null) {
                metadata.put("maxConcurrentTasks", maxConcurrentTasks.toString());
            }
        }
        return metadata;
    }

    private Map<String, Object> sanitizedMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var sanitized = new java.util.LinkedHashMap<String, Object>();
        for (var entry : metadata.entrySet()) {
            if (entry.getKey() == null || AgentOnboardingTokenValidator.isSensitiveMetadataKey(entry.getKey())) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }

    public Optional<AgentSnapshot> heartbeat(AgentHeartbeatPayload payload) {
        var record = agents.get(payload.agentId());
        if (record == null) {
            return Optional.empty();
        }
        var now = OffsetDateTime.now();
        record.lastHeartbeatAt = now;
        record.statusUpdatedAt = now;
        if (payload.status() != null) {
            record.status = payload.status();
        }
        record.currentTaskId = payload.currentTaskId();
        if (payload.runtimeLoad() != null && !payload.runtimeLoad().isEmpty()) {
            record.metadata.put("runtimeLoad", payload.runtimeLoad());
        }
        if (payload.capabilityProfile() != null && !payload.capabilityProfile().isEmpty()) {
            record.metadata.put("capabilityProfile", payload.capabilityProfile());
            Object revision = payload.capabilityProfile().get("revision");
            if (revision != null && !revision.toString().isBlank()) {
                record.metadata.put("capabilityRevision", revision.toString());
            }
            Object maxConcurrentTasks = payload.capabilityProfile().get("maxConcurrentTasks");
            if (maxConcurrentTasks != null) {
                record.metadata.put("maxConcurrentTasks", maxConcurrentTasks.toString());
            }
            Object placement = payload.capabilityProfile().get("placement");
            if (placement instanceof Map<?, ?> placementMap) {
                Object region = placementMap.get("region");
                Object zone = placementMap.get("zone");
                Object pool = placementMap.get("pool");
                if (region != null && !region.toString().isBlank()) record.metadata.put("placementRegion", region.toString());
                if (zone != null && !zone.toString().isBlank()) record.metadata.put("placementZone", zone.toString());
                if (pool != null && !pool.toString().isBlank()) record.metadata.put("placementPool", pool.toString());
            }
        }
        if (payload.metrics() != null && !payload.metrics().isEmpty()) {
            record.metadata.put("metrics", payload.metrics());
        }
        if (payload.cluster() != null && !payload.cluster().isEmpty()) {
            record.metadata.put("cluster", payload.cluster());
        }
        if (payload.plugin() != null && !payload.plugin().isEmpty()) {
            record.metadata.put("plugin", payload.plugin());
            Object pluginName = payload.plugin().get("name");
            Object pluginVersion = payload.plugin().get("version");
            if (pluginName != null) record.metadata.put("pluginName", pluginName.toString());
            if (pluginVersion != null) record.metadata.put("pluginVersion", pluginVersion.toString());
        }
        if (payload.capabilityRevision() != null && !payload.capabilityRevision().isBlank()) {
            record.metadata.put("capabilityRevision", payload.capabilityRevision());
        }
        if (payload.heartbeatId() != null && !payload.heartbeatId().isBlank()) {
            record.metadata.put("lastHeartbeatId", payload.heartbeatId());
        }
        if (payload.sequence() != null) {
            record.metadata.put("heartbeatSequence", payload.sequence());
        }
        return Optional.of(record.toSnapshot());
    }

    public Optional<AgentSnapshot> changeStatus(AgentStatusChangePayload payload) {
        var record = agents.get(payload.agentId());
        if (record == null) {
            return Optional.empty();
        }
        var now = OffsetDateTime.now();
        record.status = payload.toStatus();
        if (record.status == AgentStatus.IDLE) {
            record.currentTaskId = null;
        }
        record.statusUpdatedAt = now;
        record.lastHeartbeatAt = now;
        return Optional.of(record.toSnapshot());
    }

    public Optional<AgentSnapshot> markOfflineByConnection(ConnectionType connectionType, String endpointId) {
        for (AgentRecord record : agents.values()) {
            boolean matched = false;
            if (connectionType == ConnectionType.TCP) {
                matched = endpointId != null && endpointId.equals(record.connectionId);
            }
            if (connectionType == ConnectionType.WEBSOCKET) {
                matched = endpointId != null && endpointId.equals(record.sessionId);
            }
            if (matched) {
                record.status = AgentStatus.OFFLINE;
                record.currentTaskId = null;
                record.statusUpdatedAt = OffsetDateTime.now();
                return Optional.of(record.toSnapshot());
            }
        }
        return Optional.empty();
    }

    public List<AgentSnapshot> markTimeouts(Duration timeout) {
        var now = OffsetDateTime.now();
        var timedOut = new ArrayList<AgentSnapshot>();
        for (AgentRecord record : agents.values()) {
            if (record.status == AgentStatus.OFFLINE || record.status == AgentStatus.TIMEOUT) {
                continue;
            }
            var lastHeartbeat = record.lastHeartbeatAt == null ? record.registeredAt : record.lastHeartbeatAt;
            if (lastHeartbeat.plus(timeout).isBefore(now)) {
                record.status = AgentStatus.TIMEOUT;
                record.currentTaskId = null;
                record.statusUpdatedAt = now;
                timedOut.add(record.toSnapshot());
            }
        }
        return timedOut;
    }

    public Optional<AgentSnapshot> findById(String agentId) {
        var record = agents.get(agentId);
        return record == null ? Optional.empty() : Optional.of(record.toSnapshot());
    }

    public List<AgentSnapshot> list() {
        return agents.values().stream()
                .map(AgentRecord::toSnapshot)
                .sorted(Comparator.comparing(AgentSnapshot::registeredAt))
                .toList();
    }

    public long count() {
        return agents.size();
    }

    public long countByStatus(AgentStatus status) {
        return agents.values().stream()
                .filter(agent -> agent.status == status)
                .count();
    }

    public Map<AgentStatus, Long> countGroupByStatus() {
        var result = new EnumMap<AgentStatus, Long>(AgentStatus.class);
        for (AgentStatus status : AgentStatus.values()) {
            result.put(status, countByStatus(status));
        }
        return result;
    }

    private static final class AgentRecord {
        private final String agentId;
        private final AgentType agentType;
        private final ConnectionType connectionType;
        private final String gatewayNodeId;
        private AgentStatus status;
        private final List<String> capabilities;
        private String currentTaskId;
        private final OffsetDateTime registeredAt;
        private OffsetDateTime lastHeartbeatAt;
        private OffsetDateTime statusUpdatedAt;
        private final String remoteAddress;
        private final String connectionId;
        private final String sessionId;
        private final java.util.LinkedHashMap<String, Object> metadata;

        private AgentRecord(
                String agentId,
                AgentType agentType,
                ConnectionType connectionType,
                String gatewayNodeId,
                AgentStatus status,
                List<String> capabilities,
                String currentTaskId,
                OffsetDateTime registeredAt,
                OffsetDateTime lastHeartbeatAt,
                OffsetDateTime statusUpdatedAt,
                String remoteAddress,
                String connectionId,
                String sessionId,
                Map<String, Object> metadata
        ) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.connectionType = connectionType;
            this.gatewayNodeId = gatewayNodeId;
            this.status = status;
            this.capabilities = capabilities;
            this.currentTaskId = currentTaskId;
            this.registeredAt = registeredAt;
            this.lastHeartbeatAt = lastHeartbeatAt;
            this.statusUpdatedAt = statusUpdatedAt;
            this.remoteAddress = remoteAddress;
            this.connectionId = connectionId;
            this.sessionId = sessionId;
            this.metadata = new java.util.LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        }

        private AgentSnapshot toSnapshot() {
            return new AgentSnapshot(
                    agentId,
                    agentType,
                    connectionType,
                    gatewayNodeId,
                    status,
                    capabilities,
                    currentTaskId,
                    registeredAt,
                    lastHeartbeatAt,
                    statusUpdatedAt,
                    remoteAddress,
                    connectionId,
                    sessionId,
                    Map.copyOf(metadata)
            );
        }
    }
}
