package com.opensocket.aievent.core.routing.pool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;
import com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator;
import com.opensocket.aievent.core.routing.selection.SelectionStrategyRegistry;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Resolves the candidate Agent Pool boundary for routing decisions.
 *
 * <p>Phase 3-4 extracts target-pool lookup, manual-only empty-pool handling,
 * pool-member runtime snapshot loading, pool blocker derivation, and legacy
 * non-pool fallback candidate loading from {@code RoutingDecisionService}
 * without changing routing behaviour or diagnostic strings.</p>
 */
public class PoolResolver {
    private static final Logger log = LoggerFactory.getLogger(PoolResolver.class);

    private final AgentDirectoryFacade agentDirectory;
    private final AgentPoolRoutingRepository agentPoolRoutingRepository;
    private final RuntimeEligibilityEvaluator runtimeEligibilityEvaluator;
    private final SelectionStrategyRegistry selectionStrategyRegistry;
    private final int maxCandidates;

    public PoolResolver(AgentDirectoryFacade agentDirectory,
                        AgentPoolRoutingRepository agentPoolRoutingRepository,
                        RuntimeEligibilityEvaluator runtimeEligibilityEvaluator,
                        SelectionStrategyRegistry selectionStrategyRegistry,
                        int maxCandidates) {
        this.agentDirectory = agentDirectory;
        this.agentPoolRoutingRepository = agentPoolRoutingRepository;
        this.runtimeEligibilityEvaluator = runtimeEligibilityEvaluator;
        this.selectionStrategyRegistry = selectionStrategyRegistry;
        this.maxCandidates = maxCandidates;
    }

    public CandidateFilterResult resolve(TaskRecord task, Set<String> excluded, RoutingPolicy policy, boolean flowRuleTask) {
        String targetPoolId = task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId());
        if (flowRuleTask && blank(targetPoolId)) {
            return CandidateFilterResult.blocked(null, null, "SOURCE_FLOW_HAS_NO_DEFAULT_POOL", excluded);
        }
        if (!blank(targetPoolId)) {
            return resolveTargetPool(task, excluded, targetPoolId);
        }
        return resolveFallbackCandidates(task, excluded, policy);
    }

    private CandidateFilterResult resolveTargetPool(TaskRecord task, Set<String> excluded, String targetPoolId) {
        if (agentPoolRoutingRepository == null) {
            return CandidateFilterResult.blocked(targetPoolId, null, "AGENT_POOL_REPOSITORY_UNAVAILABLE", excluded);
        }
        AgentPoolRoutingSnapshot pool = agentPoolRoutingRepository.findActivePool(task.getTenantId(), targetPoolId).orElse(null);
        if (pool == null) {
            log.warn("routing_pool_blocker tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} blockerCode=RULE_TARGET_POOL_NOT_FOUND routingModel=AGENT_POOL_FIRST",
                    task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), targetPoolId);
            return CandidateFilterResult.blocked(targetPoolId, null, "RULE_TARGET_POOL_NOT_FOUND", excluded);
        }
        String selectionStrategy = selectionStrategyRegistry.supportedStrategy(pool.getSelectionStrategy());
        if (pool.getMembers() == null || pool.getMembers().isEmpty()) {
            if (SelectionStrategyRegistry.MANUAL_ONLY.equals(selectionStrategy)) {
                log.info("routing_manual_only_pool_without_members tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} selectionStrategy=MANUAL_ONLY selectionStrategyContract=SUPPORTED_POOL_STRATEGY",
                        task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), pool.getPoolId(), pool.getPoolCode());
                return new CandidateFilterResult(List.of(), excluded == null ? Set.of() : excluded, Set.of(), pool.getPoolId(), pool.getPoolCode(), selectionStrategy, Map.of(), null);
            }
            log.warn("routing_pool_blocker tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} blockerCode=POOL_HAS_NO_ACTIVE_MEMBER routingModel=AGENT_POOL_FIRST",
                    task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), pool.getPoolId(), pool.getPoolCode());
            return CandidateFilterResult.blocked(pool.getPoolId(), pool.getPoolCode(), "POOL_HAS_NO_ACTIVE_MEMBER", excluded);
        }

        Map<String, AgentPoolRoutingMember> membersByAgentId = new LinkedHashMap<>();
        List<AgentSnapshot> candidates = new ArrayList<>();
        for (AgentPoolRoutingMember member : pool.getMembers()) {
            if (member == null || blank(member.getAgentId())) {
                continue;
            }
            membersByAgentId.putIfAbsent(normalizeAgentId(member.getAgentId()), member);
            var runtimeSnapshot = agentDirectory.findById(member.getAgentId());
            log.info("pool_member_runtime_resolution tenantId={} taskId={} poolId={} agentId={} poolMemberStatus={} runtimeFound={} runtimeStatus={} assignable={} availableCapacity={}",
                    task.getTenantId(), task.getTaskId(), pool.getPoolId(), member.getAgentId(), String.valueOf(member.getMemberStatus()), runtimeSnapshot.isPresent(),
                    runtimeSnapshot.map(snapshot -> String.valueOf(snapshot.getStatus())).orElse("MISSING"),
                    runtimeSnapshot.map(AgentSnapshot::isAssignable).orElse(false),
                    runtimeSnapshot.map(AgentSnapshot::getAvailableSlots).orElse(0));
            runtimeSnapshot.ifPresent(candidates::add);
        }
        CandidateFilterResult filtered = runtimeEligibilityEvaluator.filterCandidates(candidates, excluded);
        String poolBlocker = filtered.included().isEmpty() ? runtimeEligibilityEvaluator.poolBlocker(pool, candidates, filtered) : null;
        log.info("routing_pool_snapshot tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} selectionStrategy={} rawSelectionStrategy={} poolMemberCount={} runtimeCandidateCount={} eligibleAgentCount={} blockerCode={} routingModel=AGENT_POOL_FIRST selectionStrategyContract=SUPPORTED_POOL_STRATEGY",
                task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                pool.getPoolId(), pool.getPoolCode(), selectionStrategy, pool.getSelectionStrategy(), pool.getMembers().size(), candidates.size(), filtered.included().size(), poolBlocker);
        return filtered.withPool(pool.getPoolId(), pool.getPoolCode(), selectionStrategy, membersByAgentId, poolBlocker);
    }

    private CandidateFilterResult resolveFallbackCandidates(TaskRecord task, Set<String> excluded, RoutingPolicy policy) {
        AgentQuery query = queryFor(task, maxCandidates, requiresLocalSearch(policy), false);
        List<AgentSnapshot> candidates = new ArrayList<>(agentDirectory.findCandidates(query));
        if (allowsGlobalFallback(policy)
                && candidates.stream().noneMatch(agent -> runtimeEligibilityEvaluator.includeForScoring(agent, excluded) && agent.isAssignable())) {
            candidates.addAll(agentDirectory.findCandidates(queryFor(task, maxCandidates, false, false)));
        }
        return runtimeEligibilityEvaluator.filterCandidates(candidates, excluded);
    }

    private AgentQuery queryFor(TaskRecord task, int limit, boolean local, boolean assignableOnly) {
        AgentQuery query = new AgentQuery();
        query.setAssignableOnly(assignableOnly);
        // Capability authority is Admin UI/Core governed assignment, not the runtime
        // AgentSnapshot capabilities_json/capability_profile_json. Do not pass business
        // capabilities into AgentDirectory.search(), because that repository can only see
        // runtime observations and would incorrectly remove perfectly eligible Admin-managed
        // Agents before backend eligibility/scoring can evaluate them.
        query.setRequiredCapabilities(List.of());
        query.setLimit(limit);
        if (local && task != null) {
            query.setSiteId(task.getSiteId());
        }
        return query;
    }

    private boolean requiresLocalSearch(RoutingPolicy policy) {
        return policy == RoutingPolicy.LOCAL_ONLY || policy == RoutingPolicy.LOCAL_FIRST;
    }

    private boolean allowsGlobalFallback(RoutingPolicy policy) {
        return policy == RoutingPolicy.LOCAL_FIRST;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            String text = value.toString();
            if (!blank(text)) return text;
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeAgentId(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(java.util.Locale.ROOT);
    }
}
