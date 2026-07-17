package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MEMORY")
public class InMemoryAgentDirectoryRepository implements AgentDirectoryRepository {
    private final ConcurrentHashMap<String, AgentSnapshot> agents = new ConcurrentHashMap<>();

    @Override
    public AgentSnapshot upsert(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (agent.getLastHeartbeatAt() == null) {
            agent.setLastHeartbeatAt(now);
        }
        agents.compute(agent.getAgentId(), (id, existing) -> {
            if (existing != null) {
                agent.setReservedTaskCount(existing.getReservedTaskCount());
            }
            return agent;
        });
        return agent;
    }

    @Override
    public Optional<AgentSnapshot> findById(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public List<AgentSnapshot> search(AgentQuery query) {
        return agents.values().stream()
                .filter(agent -> matches(query.getSiteId(), agent.getSiteId()))
                .filter(agent -> matches(query.getOwnerGatewayNodeId(), agent.getOwnerGatewayNodeId()))
                .filter(agent -> query.getStatus() == null || query.getStatus() == agent.getStatus())
                .filter(agent -> !query.isAssignableOnly() || agent.isAssignable())
                .filter(agent -> hasCapabilities(agent, query.getRequiredCapabilities()))
                .sorted(Comparator.comparing(AgentSnapshot::getLastHeartbeatAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(query.getLimit())
                .toList();
    }

    @Override
    public void updateStatus(String agentId, AgentStatus status) {
        agents.computeIfPresent(agentId, (id, agent) -> {
            agent.setStatus(status);
            agent.setLastHeartbeatAt(OffsetDateTime.now(ZoneOffset.UTC));
            return agent;
        });
    }

    @Override
    public boolean reserveCapacity(String agentId) {
        boolean[] reserved = {false};
        agents.computeIfPresent(agentId, (id, agent) -> {
            synchronized (agent) {
                if (agent.isAssignable()) {
                    agent.setReservedTaskCount(agent.getReservedTaskCount() + 1);
                    agent.setStatus(agent.getEffectiveTaskCount() >= agent.getMaxConcurrentTasks()
                            ? AgentStatus.BUSY : AgentStatus.BUSY_ACCEPTING);
                    reserved[0] = true;
                }
                return agent;
            }
        });
        return reserved[0];
    }

    @Override
    public boolean releaseCapacity(String agentId) {
        boolean[] released = {false};
        agents.computeIfPresent(agentId, (id, agent) -> {
            synchronized (agent) {
                if (agent.getReservedTaskCount() > 0) {
                    agent.setReservedTaskCount(agent.getReservedTaskCount() - 1);
                    if (agent.getStatus() != AgentStatus.OFFLINE && agent.getStatus() != AgentStatus.EXPIRED
                            && agent.getStatus() != AgentStatus.ERROR && agent.getStatus() != AgentStatus.DRAINING) {
                        agent.setStatus(agent.getEffectiveTaskCount() == 0 ? AgentStatus.IDLE
                                : agent.getEffectiveTaskCount() < agent.getMaxConcurrentTasks() ? AgentStatus.BUSY_ACCEPTING : AgentStatus.BUSY);
                    }
                    released[0] = true;
                }
                return agent;
            }
        });
        return released[0];
    }

    @Override
    public int markByGatewayNodeId(String gatewayNodeId, AgentStatus status, OffsetDateTime disconnectedAt) {
        int[] count = {0};
        agents.forEach((id, agent) -> {
            if (gatewayNodeId != null && gatewayNodeId.equals(agent.getOwnerGatewayNodeId())
                    && agent.getStatus() != AgentStatus.OFFLINE && agent.getStatus() != AgentStatus.EXPIRED) {
                agent.setStatus(status);
                agent.setDisconnectedAt(disconnectedAt);
                agent.setLastHeartbeatAt(disconnectedAt);
                count[0]++;
            }
        });
        return count[0];
    }

    @Override
    public int expireLeases(OffsetDateTime now) {
        int[] count = {0};
        agents.forEach((id, agent) -> {
            if (agent.getLeaseExpiresAt() != null
                    && agent.getLeaseExpiresAt().isBefore(now)
                    && agent.getStatus() != AgentStatus.OFFLINE
                    && agent.getStatus() != AgentStatus.EXPIRED) {
                agent.setStatus(AgentStatus.EXPIRED);
                agent.setDisconnectedAt(now);
                agent.setLastHeartbeatAt(now);
                count[0]++;
            }
        });
        return count[0];
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean hasCapabilities(AgentSnapshot agent, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Set<String> effective = effectiveCapabilities(agent);
        Set<String> normalizedRequired = required.stream()
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return effective.containsAll(normalizedRequired);
    }

    private Set<String> effectiveCapabilities(AgentSnapshot agent) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (agent.getCapabilities() != null) {
            agent.getCapabilities().stream()
                    .map(this::normalize)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(values::add);
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        if (profile != null && !profile.isEmpty()) {
            addCapabilityValues(values, profile.get("supportedCapabilities"));
            addCapabilityValues(values, profile.get("runtimeCapabilities"));
            addCapabilityValues(values, profile.get("supportedTaskTypes"));
            addCapabilityValues(values, profile.get("supportedIssueProviders"));
            addCapabilityValues(values, profile.get("toolPolicies"));
            addCapabilityValues(values, profile.get("executorMode"));
        }
        return values;
    }

    private void addCapabilityValues(Set<String> target, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCapabilityValues(target, item);
            }
            return;
        }
        String value = normalize(rawValue.toString());
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
