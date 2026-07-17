package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MEMORY")
public class InMemoryAgentRuntimeStateRepository implements AgentRuntimeStateRepository {
    private final ConcurrentHashMap<String, AgentRuntimeCapabilityProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentRuntimeDescriptor> descriptors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<AgentRuntimeCapabilityItem>> capabilityItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentRuntimeLoadSnapshot> loads = new ConcurrentHashMap<>();

    @Override
    public void upsertFromSnapshot(AgentSnapshot agent) {
        if (agent == null || blank(agent.getAgentId())) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        profiles.compute(agent.getAgentId(), (agentId, existing) -> toProfile(agent, existing == null ? now : existing.getFirstSeenAt(), now));
        descriptors.compute(agent.getAgentId(), (agentId, existing) -> toDescriptor(agent, existing == null ? now : existing.getFirstSeenAt(), now));
        capabilityItems.put(agent.getAgentId(), toItems(agent, now));
        loads.put(agent.getAgentId(), toLoad(agent, now));
    }

    @Override
    public Optional<AgentRuntimeCapabilityProfile> findCapabilityProfile(String agentId) {
        return Optional.ofNullable(profiles.get(agentId));
    }

    @Override
    public Optional<AgentRuntimeDescriptor> findRuntimeDescriptor(String agentId) {
        return Optional.ofNullable(descriptors.get(agentId));
    }

    @Override
    public List<AgentRuntimeCapabilityItem> findCapabilityItems(String agentId) {
        return List.copyOf(capabilityItems.getOrDefault(agentId, List.of()));
    }

    @Override
    public Optional<AgentRuntimeLoadSnapshot> findLoadSnapshot(String agentId) {
        return Optional.ofNullable(loads.get(agentId));
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private AgentRuntimeDescriptor toDescriptor(AgentSnapshot agent, OffsetDateTime firstSeenAt, OffsetDateTime updatedAt) {
        AgentRuntimeDescriptor descriptor = new AgentRuntimeDescriptor();
        Map<String, Object> profile = agent.getCapabilityProfile();
        Map<String, Object> runtimeLoad = agent.getRuntimeLoad();
        descriptor.setAgentId(agent.getAgentId());
        descriptor.setAgentType(agent.getAgentType());
        descriptor.setPluginName(agent.getPluginName());
        descriptor.setPluginVersion(agent.getPluginVersion());
        descriptor.setProtocolVersion(firstNonBlank(text(profile.get("protocolVersion")), text(runtimeLoad.get("protocolVersion"))));
        descriptor.setConnectionType(firstNonBlank(text(runtimeLoad.get("connectionType")), text(profile.get("connectionType")), agent.getOwnerGatewayNodeId() == null ? null : "GATEWAY"));
        descriptor.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        descriptor.setAgentSessionId(agent.getAgentSessionId());
        descriptor.setSiteId(agent.getSiteId());
        descriptor.setRegion(agent.getRegion());
        descriptor.setZone(agent.getZone());
        descriptor.setStatus(agent.getStatus());
        descriptor.setRuntimeFeatures(runtimeFeatures(agent));
        descriptor.setActiveTasks(agent.getCurrentTaskCount());
        descriptor.setMaxConcurrentTasks(agent.getMaxConcurrentTasks());
        descriptor.setAvailableSlots(agent.getAvailableSlots());
        descriptor.setCapacityUtilization(agent.getCapacityUtilization());
        descriptor.setDraining(agent.isDraining());
        descriptor.setHeartbeatSequence(longValue(runtimeLoad.get("sequence"), longValue(runtimeLoad.get("heartbeatSequence"), 0L)));
        descriptor.setConnectedAt(agent.getConnectedAt());
        descriptor.setLastHeartbeatAt(agent.getLastHeartbeatAt());
        descriptor.setLastSeenAt(agent.getLastHeartbeatAt() == null ? updatedAt : agent.getLastHeartbeatAt());
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("flatCapabilities", agent.getCapabilities());
        rawPayload.put("capabilityProfile", profile);
        rawPayload.put("runtimeLoad", runtimeLoad);
        descriptor.setRawPayload(rawPayload);
        descriptor.setFirstSeenAt(firstSeenAt);
        descriptor.setUpdatedAt(updatedAt);
        return descriptor;
    }

    private AgentRuntimeCapabilityProfile toProfile(AgentSnapshot agent, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt) {
        AgentRuntimeCapabilityProfile profile = new AgentRuntimeCapabilityProfile();
        profile.setAgentId(agent.getAgentId());
        profile.setAgentType(agent.getAgentType());
        profile.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        profile.setAgentSessionId(agent.getAgentSessionId());
        profile.setPluginName(agent.getPluginName());
        profile.setPluginVersion(agent.getPluginVersion());
        profile.setCapabilityRevision(agent.getCapabilityRevision());
        profile.setCapabilityProfile(agent.getCapabilityProfile());
        profile.setExecutorMode(text(agent.getCapabilityProfile().get("executorMode")));
        Map<String, Object> placement = asMap(agent.getCapabilityProfile().get("placement"));
        profile.setPlacementPool(text(placement.get("pool")));
        profile.setPlacementRegion(firstNonBlank(text(placement.get("region")), agent.getRegion()));
        profile.setPlacementZone(firstNonBlank(text(placement.get("zone")), agent.getZone()));
        profile.setMaxConcurrentTasks(firstPositiveInt(agent.getCapabilityProfile().get("maxConcurrentTasks"), agent.getMaxConcurrentTasks()));
        profile.setFirstSeenAt(firstSeenAt);
        profile.setLastSeenAt(lastSeenAt);
        return profile;
    }

    private List<AgentRuntimeCapabilityItem> toItems(AgentSnapshot agent, OffsetDateTime now) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        List<AgentRuntimeCapabilityItem> items = new ArrayList<>();
        addItems(items, unique, agent, "flat", agent.getCapabilities(), now);
        Map<String, Object> profile = agent.getCapabilityProfile();
        addItems(items, unique, agent, "capability", profile.get("supportedCapabilities"), now);
        addItems(items, unique, agent, "runtimeCapability", profile.get("runtimeCapabilities"), now);
        addItems(items, unique, agent, "taskType", profile.get("supportedTaskTypes"), now);
        addItems(items, unique, agent, "issueProvider", profile.get("supportedIssueProviders"), now);
        addItems(items, unique, agent, "toolPolicy", profile.get("toolPolicies"), now);
        addItems(items, unique, agent, "executorMode", profile.get("executorMode"), now);
        addSkillItems(items, unique, agent, profile.get("skills"), now);
        return List.copyOf(items);
    }

    private void addItems(List<AgentRuntimeCapabilityItem> items, LinkedHashSet<String> unique, AgentSnapshot agent, String kind, Object rawValue, OffsetDateTime now) {
        for (String value : stringValues(rawValue)) {
            String key = kind + "\u0000" + value;
            if (unique.add(key)) {
                items.add(new AgentRuntimeCapabilityItem(agent.getAgentId(), kind, value, agent.getCapabilityRevision(), "runtime", now));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addSkillItems(List<AgentRuntimeCapabilityItem> items, LinkedHashSet<String> unique, AgentSnapshot agent, Object rawValue, OffsetDateTime now) {
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

    private AgentRuntimeLoadSnapshot toLoad(AgentSnapshot agent, OffsetDateTime now) {
        AgentRuntimeLoadSnapshot load = new AgentRuntimeLoadSnapshot();
        load.setAgentId(agent.getAgentId());
        load.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        load.setAgentSessionId(agent.getAgentSessionId());
        load.setStatus(agent.getStatus());
        load.setActiveTasks(agent.getCurrentTaskCount());
        load.setMaxConcurrentTasks(agent.getMaxConcurrentTasks());
        load.setAvailableSlots(agent.getAvailableSlots());
        load.setCapacityUtilization(agent.getCapacityUtilization());
        load.setOutboxPending(agent.getOutboxPending());
        load.setOutboxInFlight(agent.getOutboxInFlight());
        load.setRecoveryPendingAssignments(agent.getRecoveryPendingAssignments());
        load.setDraining(agent.isDraining());
        load.setHeartbeatSequence(longValue(agent.getRuntimeLoad().get("sequence"), longValue(agent.getRuntimeLoad().get("heartbeatSequence"), 0L)));
        load.setRuntimeLoad(agent.getRuntimeLoad());
        load.setHeartbeatAt(agent.getLastHeartbeatAt() == null ? now : agent.getLastHeartbeatAt());
        load.setUpdatedAt(now);
        return load;
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
                if (entry.getKey() == null || Boolean.FALSE.equals(entry.getValue())) {
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

    @SuppressWarnings("unchecked")
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
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
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
