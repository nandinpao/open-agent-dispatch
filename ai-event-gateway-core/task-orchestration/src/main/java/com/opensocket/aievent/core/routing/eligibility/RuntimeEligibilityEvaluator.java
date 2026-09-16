package com.opensocket.aievent.core.routing.eligibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.routing.RoutingProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime candidate filtering and blocker derivation extracted from
 * {@code RoutingDecisionService} in Phase 3-3.
 *
 * <p>This component deliberately preserves existing routing semantics: it only
 * centralizes filtering, poison-agent exclusion, capacity-full detection, pool
 * blocker derivation, and observation blocking-reason fallback.</p>
 */
@Component
public class RuntimeEligibilityEvaluator {
    private final RoutingProperties properties;

    public RuntimeEligibilityEvaluator(RoutingProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluates the runtime facts that are common to authoritative selection
     * and read-only diagnostic projections.
     */
    public RuntimeEligibilityAssessment assess(AgentSnapshot runtime) {
        if (runtime == null) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_NOT_FOUND", "Agent has no runtime snapshot.", Map.of());
        }
        Map<String, Object> details = nullableMap(
                "status", runtime.getStatus() == null ? null : runtime.getStatus().name(),
                "ownerGatewayNodeId", runtime.getOwnerGatewayNodeId(),
                "agentSessionId", runtime.getAgentSessionId(),
                "availableSlots", runtime.getAvailableSlots(),
                "effectiveTaskCount", runtime.getEffectiveTaskCount(),
                "maxConcurrentTasks", runtime.getMaxConcurrentTasks(),
                "runtimeFailureCount", runtime.getRuntimeFailureCount());
        if (runtime.isDraining() || (runtime.getStatus() != null && "DRAINING".equals(runtime.getStatus().name()))) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_DRAINING", "Agent runtime is draining.", details);
        }
        if (runtime.isRuntimeBackoffActive()) {
            Map<String, Object> backoff = new LinkedHashMap<>(details);
            if (runtime.getRuntimeBackoffUntil() != null) backoff.put("backoffUntil", runtime.getRuntimeBackoffUntil());
            if (runtime.getRuntimeBackoffReason() != null) backoff.put("backoffReason", runtime.getRuntimeBackoffReason());
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_BACKOFF", "Agent runtime is in failure backoff.", backoff);
        }
        if (runtime.getStatus() == null || Set.of("OFFLINE", "EXPIRED", "ERROR").contains(runtime.getStatus().name())) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_NOT_READY", "Agent runtime status is not dispatch-ready.", details);
        }
        if (runtime.getOwnerGatewayNodeId() == null || runtime.getOwnerGatewayNodeId().isBlank()) {
            return RuntimeEligibilityAssessment.block("OWNER_GATEWAY_NODE_MISSING", "Agent runtime has no owner gateway node.", details);
        }
        if (runtime.getAgentSessionId() == null || runtime.getAgentSessionId().isBlank()) {
            return RuntimeEligibilityAssessment.block("AGENT_SESSION_MISSING", "Agent runtime has no active session identifier.", details);
        }
        if (isPoisonExcluded(runtime)) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_POISON_EXCLUDED", "Agent exceeded the runtime failure threshold.", details);
        }
        if (isCapacityFull(runtime)) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_CAPACITY_FULL", "Agent has no available runtime capacity.", details);
        }
        if (!runtime.isAssignable()) {
            return RuntimeEligibilityAssessment.block("AGENT_RUNTIME_NOT_ASSIGNABLE", "Agent runtime is not assignable.", details);
        }
        return RuntimeEligibilityAssessment.pass("AGENT_RUNTIME_READY", "Agent runtime identity, transport and capacity are ready.", details);
    }

    public CandidateFilterResult filterCandidates(List<AgentSnapshot> candidates, Set<String> excludedAgentIds) {
        if (candidates == null || candidates.isEmpty()) {
            return CandidateFilterResult.empty(excludedAgentIds);
        }
        Set<String> excluded = excludedAgentIds == null ? Set.of() : excludedAgentIds;
        Map<String, AgentSnapshot> unique = new LinkedHashMap<>();
        LinkedHashSet<String> reservationExcluded = new LinkedHashSet<>();
        LinkedHashSet<String> poisonExcluded = new LinkedHashSet<>();
        for (AgentSnapshot agent : candidates) {
            if (agent == null || agent.getAgentId() == null || agent.getAgentId().isBlank()) {
                continue;
            }
            String agentId = agent.getAgentId();
            if (excluded.contains(agentId)) {
                reservationExcluded.add(agentId);
                continue;
            }
            if (isPoisonExcluded(agent)) {
                poisonExcluded.add(agentId);
                continue;
            }
            unique.putIfAbsent(agentId, agent);
        }
        return new CandidateFilterResult(new ArrayList<>(unique.values()), reservationExcluded, poisonExcluded, null, null, null, Map.of(), null);
    }

    public boolean includeForScoring(AgentSnapshot agent, Set<String> excluded) {
        return agent != null
                && agent.getAgentId() != null
                && !agent.getAgentId().isBlank()
                && (excluded == null || !excluded.contains(agent.getAgentId()))
                && !isPoisonExcluded(agent);
    }

    public boolean isPoisonExcluded(AgentSnapshot agent) {
        if (properties == null || !properties.isPoisonAgentExclusionEnabled() || agent == null) {
            return false;
        }
        int threshold = properties.getPoisonAgentFailureThreshold();
        return threshold > 0 && agent.getRuntimeFailureCount() >= threshold;
    }

    public String poolBlocker(AgentPoolRoutingSnapshot pool, List<AgentSnapshot> rawCandidates, CandidateFilterResult filtered) {
        if (pool == null || pool.getMembers() == null || pool.getMembers().isEmpty()) {
            return "POOL_HAS_NO_ACTIVE_MEMBER";
        }
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return "POOL_AGENT_RUNTIME_NOT_FOUND";
        }
        boolean anyOffline = rawCandidates.stream().anyMatch(agent -> agent != null && agent.getStatus() != null
                && Set.of("OFFLINE", "EXPIRED", "ERROR").contains(agent.getStatus().name()));
        if (anyOffline) {
            return "POOL_AGENT_OFFLINE";
        }
        boolean allCapacityFull = rawCandidates.stream().allMatch(this::isCapacityFull);
        if (allCapacityFull) {
            return "POOL_AGENT_CAPACITY_FULL";
        }
        if (filtered != null && !filtered.poisonExcluded().isEmpty()) {
            return "POOL_AGENT_BACKOFF";
        }
        return "NO_ELIGIBLE_AGENT_IN_POOL";
    }

    public boolean isCapacityFull(AgentSnapshot agent) {
        if (agent == null) {
            return false;
        }
        return agent.getAvailableSlots() <= 0
                && agent.getEffectiveTaskCount() >= Math.max(1, agent.getMaxConcurrentTasks());
    }

    public String candidateBlockingReason(boolean enforceGlobalReasons, List<String> globalReasonCodes, CandidateFilterResult pool) {
        if (enforceGlobalReasons && globalReasonCodes != null && !globalReasonCodes.isEmpty()) {
            return normalizeObservationValue(globalReasonCodes.getFirst());
        }
        if (pool != null && !blank(pool.poolBlockerCode())) {
            return pool.poolBlockerCode().toLowerCase(Locale.ROOT);
        }
        if (pool != null && pool.included().isEmpty()) {
            if (!pool.reservationExcluded().isEmpty()) return "reservation_excluded";
            if (!pool.poisonExcluded().isEmpty()) return "poison_agent_excluded";
        }
        return "no_candidate";
    }

    private String normalizeObservationValue(String value) {
        return value == null || value.isBlank() ? "none" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
    private Map<String, Object> nullableMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            Object value = values[i + 1];
            if (key != null && value != null) result.put(String.valueOf(key), value);
        }
        return result;
    }

}
