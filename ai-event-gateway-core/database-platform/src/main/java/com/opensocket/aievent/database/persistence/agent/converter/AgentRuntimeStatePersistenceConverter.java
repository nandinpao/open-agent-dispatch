package com.opensocket.aievent.database.persistence.agent.converter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityProfile;
import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.AgentRuntimeLoadSnapshot;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeCapabilityItemPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeCapabilityProfilePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeDescriptorPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeLoadSnapshotPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class AgentRuntimeStatePersistenceConverter {
    private final ObjectMapper objectMapper;

    public AgentRuntimeStatePersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentRuntimeCapabilityProfilePo toCapabilityProfilePo(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentRuntimeCapabilityProfilePo po = new AgentRuntimeCapabilityProfilePo();
        po.setAgentId(agent.getAgentId());
        po.setAgentType(agent.getAgentType());
        po.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        po.setAgentSessionId(agent.getAgentSessionId());
        po.setPluginName(agent.getPluginName());
        po.setPluginVersion(agent.getPluginVersion());
        po.setCapabilityRevision(agent.getCapabilityRevision());
        po.setCapabilityProfileJson(toObjectJson(agent.getCapabilityProfile()));
        po.setExecutorMode(text(agent.getCapabilityProfile().get("executorMode")));
        Map<String, Object> placement = asMap(agent.getCapabilityProfile().get("placement"));
        po.setPlacementPool(text(placement.get("pool")));
        po.setPlacementRegion(firstNonBlank(text(placement.get("region")), agent.getRegion()));
        po.setPlacementZone(firstNonBlank(text(placement.get("zone")), agent.getZone()));
        po.setMaxConcurrentTasks(firstPositiveInt(agent.getCapabilityProfile().get("maxConcurrentTasks"), agent.getMaxConcurrentTasks()));
        po.setFirstSeenAt(now);
        po.setLastSeenAt(now);
        return po;
    }


    public AgentRuntimeDescriptorPo toRuntimeDescriptorPo(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> profile = agent.getCapabilityProfile();
        Map<String, Object> runtimeLoad = agent.getRuntimeLoad();
        AgentRuntimeDescriptorPo po = new AgentRuntimeDescriptorPo();
        po.setAgentId(agent.getAgentId());
        po.setAgentType(agent.getAgentType());
        po.setPluginName(agent.getPluginName());
        po.setPluginVersion(agent.getPluginVersion());
        po.setProtocolVersion(firstNonBlank(text(profile.get("protocolVersion")), text(runtimeLoad.get("protocolVersion"))));
        po.setConnectionType(firstNonBlank(text(runtimeLoad.get("connectionType")), text(profile.get("connectionType")), agent.getOwnerGatewayNodeId() == null ? null : "GATEWAY"));
        po.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        po.setAgentSessionId(agent.getAgentSessionId());
        po.setSiteId(agent.getSiteId());
        po.setRegion(agent.getRegion());
        po.setZone(agent.getZone());
        po.setStatus(agent.getStatus() == null ? null : agent.getStatus().name());
        po.setRuntimeFeaturesJson(toStringListJson(runtimeFeatures(agent)));
        po.setActiveTasks(agent.getCurrentTaskCount());
        po.setMaxConcurrentTasks(agent.getMaxConcurrentTasks());
        po.setAvailableSlots(agent.getAvailableSlots());
        po.setCapacityUtilization(agent.getCapacityUtilization());
        po.setDraining(agent.isDraining());
        po.setHeartbeatSequence(longValue(runtimeLoad.get("sequence"), longValue(runtimeLoad.get("heartbeatSequence"), 0L)));
        po.setConnectedAt(agent.getConnectedAt());
        po.setLastHeartbeatAt(agent.getLastHeartbeatAt());
        po.setLastSeenAt(agent.getLastHeartbeatAt() == null ? now : agent.getLastHeartbeatAt());
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("flatCapabilities", agent.getCapabilities());
        rawPayload.put("capabilityProfile", profile);
        rawPayload.put("runtimeLoad", runtimeLoad);
        po.setRawPayloadJson(toObjectJson(rawPayload));
        po.setFirstSeenAt(now);
        po.setUpdatedAt(now);
        return po;
    }

    public List<AgentRuntimeCapabilityItemPo> toCapabilityItemPos(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        List<AgentRuntimeCapabilityItemPo> items = new ArrayList<>();
        addItems(items, unique, agent, "flat", agent.getCapabilities(), now);
        Map<String, Object> profile = agent.getCapabilityProfile();
        addItems(items, unique, agent, "capability", profile.get("supportedCapabilities"), now);
        addItems(items, unique, agent, "runtimeCapability", profile.get("runtimeCapabilities"), now);
        addItems(items, unique, agent, "taskType", profile.get("supportedTaskTypes"), now);
        addItems(items, unique, agent, "issueProvider", profile.get("supportedIssueProviders"), now);
        addItems(items, unique, agent, "toolPolicy", profile.get("toolPolicies"), now);
        addItems(items, unique, agent, "executorMode", profile.get("executorMode"), now);
        addSkillItems(items, unique, agent, profile.get("skills"), now);
        return items;
    }

    public AgentRuntimeLoadSnapshotPo toRuntimeLoadPo(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentRuntimeLoadSnapshotPo po = new AgentRuntimeLoadSnapshotPo();
        po.setAgentId(agent.getAgentId());
        po.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        po.setAgentSessionId(agent.getAgentSessionId());
        po.setStatus(agent.getStatus() == null ? null : agent.getStatus().name());
        po.setActiveTasks(agent.getCurrentTaskCount());
        po.setMaxConcurrentTasks(agent.getMaxConcurrentTasks());
        po.setAvailableSlots(agent.getAvailableSlots());
        po.setCapacityUtilization(agent.getCapacityUtilization());
        po.setOutboxPending(agent.getOutboxPending());
        po.setOutboxInFlight(agent.getOutboxInFlight());
        po.setRecoveryPendingAssignments(agent.getRecoveryPendingAssignments());
        po.setDraining(agent.isDraining());
        po.setHeartbeatSequence(longValue(agent.getRuntimeLoad().get("sequence"), longValue(agent.getRuntimeLoad().get("heartbeatSequence"), 0L)));
        po.setRuntimeLoadJson(toObjectJson(agent.getRuntimeLoad()));
        po.setHeartbeatAt(agent.getLastHeartbeatAt() == null ? now : agent.getLastHeartbeatAt());
        po.setUpdatedAt(now);
        return po;
    }


    public AgentRuntimeDescriptor toRuntimeDescriptor(AgentRuntimeDescriptorPo po) {
        AgentRuntimeDescriptor descriptor = new AgentRuntimeDescriptor();
        descriptor.setAgentId(po.getAgentId());
        descriptor.setAgentType(po.getAgentType());
        descriptor.setPluginName(po.getPluginName());
        descriptor.setPluginVersion(po.getPluginVersion());
        descriptor.setProtocolVersion(po.getProtocolVersion());
        descriptor.setConnectionType(po.getConnectionType());
        descriptor.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
        descriptor.setAgentSessionId(po.getAgentSessionId());
        descriptor.setSiteId(po.getSiteId());
        descriptor.setRegion(po.getRegion());
        descriptor.setZone(po.getZone());
        descriptor.setStatus(po.getStatus() == null ? null : AgentStatus.valueOf(po.getStatus()));
        descriptor.setRuntimeFeatures(fromStringListJson(po.getRuntimeFeaturesJson()));
        descriptor.setActiveTasks(po.getActiveTasks());
        descriptor.setMaxConcurrentTasks(po.getMaxConcurrentTasks());
        descriptor.setAvailableSlots(po.getAvailableSlots());
        descriptor.setCapacityUtilization(po.getCapacityUtilization());
        descriptor.setDraining(po.isDraining());
        descriptor.setHeartbeatSequence(po.getHeartbeatSequence());
        descriptor.setConnectedAt(po.getConnectedAt());
        descriptor.setLastHeartbeatAt(po.getLastHeartbeatAt());
        descriptor.setLastSeenAt(po.getLastSeenAt());
        descriptor.setRawPayload(fromObjectJson(po.getRawPayloadJson()));
        descriptor.setFirstSeenAt(po.getFirstSeenAt());
        descriptor.setUpdatedAt(po.getUpdatedAt());
        return descriptor;
    }

    public AgentRuntimeCapabilityProfile toCapabilityProfile(AgentRuntimeCapabilityProfilePo po) {
        AgentRuntimeCapabilityProfile profile = new AgentRuntimeCapabilityProfile();
        profile.setAgentId(po.getAgentId());
        profile.setAgentType(po.getAgentType());
        profile.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
        profile.setAgentSessionId(po.getAgentSessionId());
        profile.setPluginName(po.getPluginName());
        profile.setPluginVersion(po.getPluginVersion());
        profile.setCapabilityRevision(po.getCapabilityRevision());
        profile.setExecutorMode(po.getExecutorMode());
        profile.setPlacementPool(po.getPlacementPool());
        profile.setPlacementRegion(po.getPlacementRegion());
        profile.setPlacementZone(po.getPlacementZone());
        profile.setMaxConcurrentTasks(po.getMaxConcurrentTasks());
        profile.setCapabilityProfile(fromObjectJson(po.getCapabilityProfileJson()));
        profile.setFirstSeenAt(po.getFirstSeenAt());
        profile.setLastSeenAt(po.getLastSeenAt());
        return profile;
    }

    public AgentRuntimeCapabilityItem toCapabilityItem(AgentRuntimeCapabilityItemPo po) {
        return new AgentRuntimeCapabilityItem(
                po.getAgentId(),
                po.getCapabilityKind(),
                po.getCapabilityValue(),
                po.getCapabilityRevision(),
                po.getSource(),
                po.getUpdatedAt()
        );
    }

    public AgentRuntimeLoadSnapshot toRuntimeLoad(AgentRuntimeLoadSnapshotPo po) {
        AgentRuntimeLoadSnapshot load = new AgentRuntimeLoadSnapshot();
        load.setAgentId(po.getAgentId());
        load.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
        load.setAgentSessionId(po.getAgentSessionId());
        load.setStatus(po.getStatus() == null ? null : AgentStatus.valueOf(po.getStatus()));
        load.setActiveTasks(po.getActiveTasks());
        load.setMaxConcurrentTasks(po.getMaxConcurrentTasks());
        load.setAvailableSlots(po.getAvailableSlots());
        load.setCapacityUtilization(po.getCapacityUtilization());
        load.setOutboxPending(po.getOutboxPending());
        load.setOutboxInFlight(po.getOutboxInFlight());
        load.setRecoveryPendingAssignments(po.getRecoveryPendingAssignments());
        load.setDraining(po.isDraining());
        load.setHeartbeatSequence(po.getHeartbeatSequence());
        load.setRuntimeLoad(fromObjectJson(po.getRuntimeLoadJson()));
        load.setHeartbeatAt(po.getHeartbeatAt());
        load.setUpdatedAt(po.getUpdatedAt());
        return load;
    }

    private void addItems(List<AgentRuntimeCapabilityItemPo> items, LinkedHashSet<String> unique, AgentSnapshot agent, String kind, Object rawValue, OffsetDateTime now) {
        for (String value : stringValues(rawValue)) {
            String key = kind + "\u0000" + value;
            if (!unique.add(key)) {
                continue;
            }
            AgentRuntimeCapabilityItemPo po = new AgentRuntimeCapabilityItemPo();
            po.setAgentId(agent.getAgentId());
            po.setCapabilityKind(kind);
            po.setCapabilityValue(value);
            po.setCapabilityRevision(agent.getCapabilityRevision());
            po.setSource("runtime");
            po.setUpdatedAt(now);
            items.add(po);
        }
    }

    @SuppressWarnings("unchecked")
    private void addSkillItems(List<AgentRuntimeCapabilityItemPo> items, LinkedHashSet<String> unique, AgentSnapshot agent, Object rawValue, OffsetDateTime now) {
        if (!(rawValue instanceof Iterable<?> skills)) {
            return;
        }
        for (Object rawSkill : skills) {
            if (!(rawSkill instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> skill = new LinkedHashMap<>();
            for (var entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    skill.put(entry.getKey().toString(), entry.getValue());
                }
            }
            addItems(items, unique, agent, "skill", skill.get("skillCode"), now);
            addItems(items, unique, agent, "skillTaskType", skill.get("taskTypes"), now);
            addItems(items, unique, agent, "skillProvider", skill.get("providers"), now);
            addItems(items, unique, agent, "skillOperation", skill.get("operations"), now);
            addItems(items, unique, agent, "skillToolPolicy", skill.get("toolPolicies"), now);
            addItems(items, unique, agent, "skillDataClass", skill.get("dataClasses"), now);
        }
    }


    public String toStringListJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public List<String> fromStringListJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> runtimeFeatures(AgentSnapshot agent) {
        LinkedHashSet<String> features = new LinkedHashSet<>();
        addRuntimeFeatureValues(features, agent.getCapabilities());
        Map<String, Object> profile = agent.getCapabilityProfile();
        addRuntimeFeatureValues(features, profile.get("runtimeFeatures"));
        addRuntimeFeatureValues(features, profile.get("protocolFeatures"));
        addRuntimeFeatureValues(features, profile.get("features"));
        addRuntimeFeatureValues(features, agent.getRuntimeLoad().get("runtimeFeatures"));
        addRuntimeFeatureValues(features, agent.getRuntimeLoad().get("protocolFeatures"));
        return List.copyOf(features);
    }

    private void addRuntimeFeatureValues(LinkedHashSet<String> features, Object rawValue) {
        if (rawValue instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object value = entry.getValue();
                if (Boolean.FALSE.equals(value)) {
                    continue;
                }
                String feature = text(entry.getKey());
                if (isRuntimeFeature(feature)) {
                    features.add(feature);
                }
            }
            return;
        }
        for (String value : stringValues(rawValue)) {
            if (isRuntimeFeature(value)) {
                features.add(value);
            }
        }
    }

    private boolean isRuntimeFeature(String value) {
        if (blank(value)) {
            return false;
        }
        String upper = value.trim().toUpperCase();
        return upper.startsWith("TASK")
                || upper.startsWith("HEARTBEAT")
                || upper.startsWith("RUNTIME")
                || upper.startsWith("CLUSTER")
                || upper.startsWith("SESSION")
                || upper.startsWith("AGENT_")
                || upper.startsWith("CAPABILITY_PROFILE")
                || upper.startsWith("DRAIN")
                || upper.contains("ACK")
                || upper.contains("CANCEL")
                || upper.contains("LEASE")
                || upper.contains("RECONCILE")
                || upper.contains("OWNERSHIP")
                || upper.contains("ROUTING")
                || upper.contains("LOAD");
    }

    public String toObjectJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public Map<String, Object> fromObjectJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> stringValues(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                String value = text(item);
                if (!blank(value)) {
                    values.add(value);
                }
            }
            return values;
        }
        String value = text(rawValue);
        return blank(value) ? List.of() : List.of(value);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private int firstPositiveInt(Object value, int fallback) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
