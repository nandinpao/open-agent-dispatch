package com.opensocket.aievent.database.persistence.agent.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.database.persistence.agent.po.AgentSnapshotPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class AgentDirectoryPersistenceConverter {
    private final ObjectMapper objectMapper;

    public AgentDirectoryPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentSnapshotPo toPo(AgentSnapshot agent) {
            AgentSnapshotPo po = new AgentSnapshotPo();
            po.setAgentId(agent.getAgentId());
            po.setAgentType(agent.getAgentType());
            po.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
            po.setAgentSessionId(agent.getAgentSessionId());
            po.setSiteId(agent.getSiteId());
            po.setSiteName(agent.getSiteName());
            po.setRegion(agent.getRegion());
            po.setZone(agent.getZone());
            po.setStatus(agent.getStatus() == null ? null : agent.getStatus().name());
            po.setCapabilitiesJson(toJson(agent.getCapabilities()));
            po.setCurrentTaskCount(agent.getCurrentTaskCount());
            po.setReservedTaskCount(agent.getReservedTaskCount());
            po.setMaxConcurrentTasks(agent.getMaxConcurrentTasks());
            po.setHealthScore(agent.getHealthScore());
            po.setCapabilityProfileJson(toObjectJson(agent.getCapabilityProfile()));
            po.setRuntimeLoadJson(toObjectJson(agent.getRuntimeLoad()));
            po.setPluginName(agent.getPluginName());
            po.setPluginVersion(agent.getPluginVersion());
            po.setCapabilityRevision(agent.getCapabilityRevision());
            po.setAvailableSlots(agent.getAvailableSlots());
            po.setCapacityUtilization(agent.getCapacityUtilization());
            po.setOutboxPending(agent.getOutboxPending());
            po.setOutboxInFlight(agent.getOutboxInFlight());
            po.setRecoveryPendingAssignments(agent.getRecoveryPendingAssignments());
            po.setDraining(agent.isDraining());
            po.setConnectedAt(agent.getConnectedAt());
            po.setLastHeartbeatAt(agent.getLastHeartbeatAt());
            po.setDisconnectedAt(agent.getDisconnectedAt());
            po.setLeaseExpiresAt(agent.getLeaseExpiresAt());
            po.setRuntimeBackoffUntil(agent.getRuntimeBackoffUntil());
            po.setRuntimeBackoffReason(agent.getRuntimeBackoffReason());
            po.setRuntimeFailureCount(agent.getRuntimeFailureCount());
            return po;
        }

    public AgentSnapshot toAgent(AgentSnapshotPo po) {
            AgentSnapshot agent = new AgentSnapshot();
            agent.setAgentId(po.getAgentId());
            agent.setAgentType(po.getAgentType());
            agent.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
            agent.setAgentSessionId(po.getAgentSessionId());
            agent.setSiteId(po.getSiteId());
            agent.setSiteName(po.getSiteName());
            agent.setRegion(po.getRegion());
            agent.setZone(po.getZone());
            agent.setStatus(po.getStatus() == null ? null : AgentStatus.valueOf(po.getStatus()));
            agent.setCapabilities(fromJson(po.getCapabilitiesJson()));
            agent.setCurrentTaskCount(po.getCurrentTaskCount());
            agent.setReservedTaskCount(po.getReservedTaskCount());
            agent.setMaxConcurrentTasks(po.getMaxConcurrentTasks());
            agent.setHealthScore(po.getHealthScore());
            agent.setCapabilityProfile(fromObjectJson(po.getCapabilityProfileJson()));
            agent.setRuntimeLoad(fromObjectJson(po.getRuntimeLoadJson()));
            agent.setPluginName(po.getPluginName());
            agent.setPluginVersion(po.getPluginVersion());
            agent.setCapabilityRevision(po.getCapabilityRevision());
            agent.setAvailableSlots(po.getAvailableSlots());
            agent.setCapacityUtilization(po.getCapacityUtilization());
            agent.setOutboxPending(po.getOutboxPending());
            agent.setOutboxInFlight(po.getOutboxInFlight());
            agent.setRecoveryPendingAssignments(po.getRecoveryPendingAssignments());
            agent.setDraining(po.isDraining());
            agent.setConnectedAt(po.getConnectedAt());
            agent.setLastHeartbeatAt(po.getLastHeartbeatAt());
            agent.setDisconnectedAt(po.getDisconnectedAt());
            agent.setLeaseExpiresAt(po.getLeaseExpiresAt());
            agent.setRuntimeBackoffUntil(po.getRuntimeBackoffUntil());
            agent.setRuntimeBackoffReason(po.getRuntimeBackoffReason());
            agent.setRuntimeFailureCount(po.getRuntimeFailureCount());
            return agent;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception ex) { return "[]"; }
        }


    public String toObjectJson(Map<String, Object> value) {
            try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); } catch (Exception ex) { return "{}"; }
        }

    public List<String> fromJson(String json) {
            try { if (json == null || json.isBlank()) return List.of(); return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); }
        }

    public Map<String, Object> fromObjectJson(String json) {
            try { if (json == null || json.isBlank()) return Map.of(); return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (Exception ex) { return Map.of(); }
        }
}
