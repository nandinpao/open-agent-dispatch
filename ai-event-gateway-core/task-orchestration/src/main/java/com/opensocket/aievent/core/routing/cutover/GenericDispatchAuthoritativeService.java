package com.opensocket.aievent.core.routing.cutover;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.routing.governance.DispatchRequirementResolution;
import com.opensocket.aievent.core.routing.governance.DispatchRequirementResolver;
import com.opensocket.aievent.core.routing.governance.GenericRoutingStrategy;
import com.opensocket.aievent.core.routing.governance.RequirementDecisionStatus;
import com.opensocket.aievent.core.routing.governance.RequirementResolutionMode;
import com.opensocket.aievent.core.routing.governance.SideEffectLevel;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.routing.governance.eligibility.GenericDispatchEligibilityService;
import com.opensocket.aievent.core.routing.governance.eligibility.TaskAgentEligibilityShadowComparison;
import com.opensocket.aievent.core.routing.governance.routing.CandidateAgentProvider;
import com.opensocket.aievent.core.routing.governance.routing.GenericCandidateAgent;
import com.opensocket.aievent.core.routing.governance.routing.GenericRoutingScore;
import com.opensocket.aievent.core.routing.governance.routing.GenericRoutingScoreCalculator;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Canonical pool-first routing implementation used exclusively by
 * {@code DispatchDecisionEngine}.
 *
 * <p>V38-7A1 makes this the execution authority for Source Flow and governed
 * Agent Pool work. The invariant is:</p>
 *
 * <pre>
 * Source Flow / upstream governance -> Agent Pool membership
 * -> Core-approved required Capability -> Agent approval/credential
 * -> Runtime readiness/capacity -> ranking -> selected Agent
 * </pre>
 *
 * <p>Runtime-reported capabilities are not consulted as qualification authority.</p>
 */
@Service
public class GenericDispatchAuthoritativeService {
    private static final int GOVERNED_POOL_RESOLVER_VERSION = 4;

    private final FlowRuleRoutingService flowRuleRoutingService;
    private final DispatchRequirementResolver requirementResolver;
    private final CandidateAgentProvider candidateProvider;
    private final GenericDispatchEligibilityService eligibilityService;
    private final GenericRoutingScoreCalculator scoreCalculator;
    private final RoutingProperties properties;

    public GenericDispatchAuthoritativeService(FlowRuleRoutingService flowRuleRoutingService,
            DispatchRequirementResolver requirementResolver,
            CandidateAgentProvider candidateProvider,
            GenericDispatchEligibilityService eligibilityService,
            GenericRoutingScoreCalculator scoreCalculator,
            RoutingProperties properties) {
        this.flowRuleRoutingService = flowRuleRoutingService;
        this.requirementResolver = requirementResolver;
        this.candidateProvider = candidateProvider;
        this.eligibilityService = eligibilityService;
        this.scoreCalculator = scoreCalculator;
        this.properties = properties;
    }

    public GenericAuthoritativeRoutingResult route(TaskRecord task, Set<String> excludedAgentIds) {
        try {
            TaskRequirementEvidence requirement;
            if (isGovernedPoolTask(task)) {
                requirement = governedPoolRequirement(task);
            } else {
                FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);
                if (plan == null || !plan.isMatched()) {
                    return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.REQUIREMENT_BLOCKED,
                            null, List.of(), List.of(), null, "FLOW_RULE_NOT_MATCHED", "No persisted Source Flow Rule matched the Task");
                }
                requirement = toEvidence(requirementResolver.resolve(task, plan));
            }

            applyCanonicalCapabilityContract(requirement);
            if (requirement.getDecisionStatus() == RequirementDecisionStatus.BLOCKED) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.REQUIREMENT_BLOCKED,
                        requirement, List.of(), List.of(), null, requirement.getReasonCode(), "Canonical requirement resolution blocked the Task");
            }
            if (requirement.getRoutingStrategy() == GenericRoutingStrategy.MANUAL_REVIEW) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.MANUAL_REVIEW,
                        requirement, List.of(), List.of(), null, "MANUAL_REVIEW_REQUIRED", "Agent Pool selection strategy requires manual review");
            }

            Map<String, GenericCandidateAgent> pool = candidateProvider.provide(task, requirement, List.of());
            List<AgentCandidateScore> scores = new ArrayList<>();
            List<GenericAuthoritativeRoutingResult.BlockedCandidateEvidence> blockedCandidates = new ArrayList<>();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Set<String> excluded = excludedAgentIds == null ? Set.of() : excludedAgentIds;
            for (Map.Entry<String, GenericCandidateAgent> entry : pool.entrySet()) {
                String agentId = entry.getKey();
                if (excluded.contains(agentId)) {
                    blockedCandidates.add(new GenericAuthoritativeRoutingResult.BlockedCandidateEvidence(agentId, runtimeStatus(entry.getValue().getRuntime()), List.of("AGENT_EXCLUDED")));
                    continue;
                }
                AgentSnapshot runtime = entry.getValue().getRuntime();
                if (runtime != null && properties.isPoisonAgentExclusionEnabled()
                        && properties.getPoisonAgentFailureThreshold() > 0
                        && runtime.getRuntimeFailureCount() >= properties.getPoisonAgentFailureThreshold()) {
                    blockedCandidates.add(new GenericAuthoritativeRoutingResult.BlockedCandidateEvidence(agentId, runtimeStatus(runtime), List.of("POOL_AGENT_BACKOFF")));
                    continue;
                }
                TaskAgentEligibilityShadowComparison eligibility = eligibilityService.evaluateCandidateAuthoritatively(
                        task, requirement, runtime, agentId, now);
                if (!eligibility.isShadowEligible()) {
                    blockedCandidates.add(new GenericAuthoritativeRoutingResult.BlockedCandidateEvidence(
                            agentId, runtimeStatus(runtime), eligibility.getBlockingReasonCodes()));
                    continue;
                }
                GenericRoutingScore genericScore = scoreCalculator.score(requirement.getRoutingStrategy(), task, requirement, runtime);
                scores.add(toCandidateScore(entry.getValue(), requirement, genericScore, eligibility));
            }
            scores = scores.stream().sorted(Comparator.comparingInt(AgentCandidateScore::score).reversed()
                    .thenComparing(AgentCandidateScore::agentId)).toList();
            if (scores.isEmpty()) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.NO_CANDIDATE,
                        requirement, scores, blockedCandidates, null, firstBlockingReason(blockedCandidates, "NO_CANONICAL_ELIGIBLE_AGENT"),
                        "No Agent Pool member passed the canonical Capability, Agent governance, runtime and capacity eligibility checks");
            }
            boolean thresholdApplies = scoreThresholdApplies(requirement);
            if (thresholdApplies && scores.getFirst().score() < properties.getMinimumScore()) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.NO_CANDIDATE,
                        requirement, scores, blockedCandidates, null, "CANONICAL_SCORE_BELOW_MINIMUM",
                        "Highest eligible candidate score " + scores.getFirst().score()
                                + " is below minimum score " + properties.getMinimumScore());
            }
            return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.SELECTED,
                    requirement, scores, blockedCandidates, scores.getFirst(), "CANONICAL_AGENT_SELECTED",
                    "Canonical dispatch authority selected the highest ranked eligible Agent Pool member"
                            + (thresholdApplies ? " after minimum score validation"
                                    : "; score threshold bypassed for non-quality ranking strategy "
                                            + (requirement.getRoutingStrategy() == null ? "-" : requirement.getRoutingStrategy().name())));
        } catch (RuntimeException ex) {
            return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.ERROR,
                    null, List.of(), List.of(), null, "CANONICAL_ROUTING_ERROR", ex.getClass().getSimpleName() + ": " + safe(ex.getMessage()));
        }
    }

    private TaskRequirementEvidence governedPoolRequirement(TaskRecord task) {
        if (task == null) throw new IllegalArgumentException("task is required");
        String targetPoolId = firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId());
        if (blank(targetPoolId)) {
            throw new IllegalArgumentException("Governed pool Task requires targetPoolId");
        }
        List<String> requiredCapabilities = normalized(task.getRequiredCapabilities());
        TaskRequirementEvidence evidence = new TaskRequirementEvidence();
        evidence.setTenantId(require(task.getTenantId(), "tenantId"));
        evidence.setEvidenceId("governed-pool-req-" + UUID.randomUUID());
        evidence.setTaskId(require(task.getTaskId(), "taskId"));
        evidence.setMatchedFlowId(task.getMatchedFlowId());
        evidence.setMatchedRuleId(task.getMatchedRuleId());
        evidence.setSourceSystem(require(firstNonBlank(task.getSourceSystem(), task.getOriginSourceSystem()), "sourceSystem"));
        evidence.setResolutionMode(requiredCapabilities.isEmpty()
                ? RequirementResolutionMode.NONE : RequirementResolutionMode.EXPLICIT_CAPABILITY);
        evidence.setRequiredOperations(List.of());
        evidence.setRequiredCapabilities(requiredCapabilities);
        evidence.setSideEffectLevel(SideEffectLevel.NONE);
        evidence.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL);
        evidence.setRoutingStrategy(GenericRoutingStrategy.LOWEST_LOAD);
        evidence.setExplicitActionAuthorizationRequired(false);
        evidence.setDecisionStatus(RequirementDecisionStatus.RESOLVED);
        evidence.setReasonCode(requiredCapabilities.isEmpty()
                ? "GOVERNED_POOL_NO_CAPABILITY_REQUIREMENT_RESOLVED"
                : "GOVERNED_POOL_CAPABILITY_REQUIREMENT_RESOLVED");
        evidence.setResolverVersion(GOVERNED_POOL_RESOLVER_VERSION);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dispatchAuthority", "DISPATCH_DECISION_ENGINE");
        details.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        details.put("authoritySource", "UPSTREAM_GOVERNANCE");
        details.put("targetPoolId", targetPoolId);
        details.put("a2aPolicyId", task.getA2aPolicyId());
        details.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        details.put("runtimeReportedCapabilitiesAuthority", false);
        details.put("routingSequence", "GOVERNED_POOL->REQUIRED_CAPABILITY->RUNTIME_ELIGIBILITY->ROUTING_SCORE");
        evidence.setEvidence(details);
        evidence.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        evidence.setCreatedBy("GOVERNED_POOL_REQUIREMENT_RESOLVER");
        evidence.validate();
        return evidence;
    }

    /**
     * V38 canonical Capability routing authority.
     *
     * <p>Pool membership limits where to look. Required Capability remains a blocking
     * Task requirement. Only Core-approved Agent Capability assignments satisfy it.</p>
     */
    private void applyCanonicalCapabilityContract(TaskRequirementEvidence requirement) {
        if (requirement == null) return;
        Map<String, Object> evidence = new LinkedHashMap<>(requirement.getEvidence());
        evidence.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        evidence.put("taskRequirementAuthority", "TASK_REQUIRED_CAPABILITY");
        evidence.put("capabilityPolicyMode", "CANONICAL_REQUIRED_CAPABILITY");
        evidence.put("requiredCapabilityMatchMode", "ALL");
        evidence.put("runtimeReportedCapabilitiesAuthority", false);
        evidence.put("poolCapabilityRoutingAuthorityRetired", true);
        evidence.put("routingSequence", "FLOW_OR_GOVERNANCE_POOL->REQUIRED_CAPABILITY->RUNTIME_ELIGIBILITY->ROUTING_SCORE");
        requirement.setEvidence(evidence);
    }

    private boolean scoreThresholdApplies(TaskRequirementEvidence requirement) {
        GenericRoutingStrategy strategy = requirement == null || requirement.getRoutingStrategy() == null
                ? GenericRoutingStrategy.WEIGHTED_SCORE
                : requirement.getRoutingStrategy();
        return strategy != GenericRoutingStrategy.ROUND_ROBIN && strategy != GenericRoutingStrategy.LOWEST_LOAD;
    }

    private TaskRequirementEvidence toEvidence(DispatchRequirementResolution resolution) {
        if (resolution == null) {
            throw new IllegalStateException("Dispatch requirement resolution is required");
        }
        TaskRequirementEvidence evidence = new TaskRequirementEvidence();
        evidence.setTenantId(resolution.getTenantId());
        evidence.setEvidenceId("canonical-req-" + UUID.randomUUID());
        evidence.setTaskId(resolution.getTaskId());
        evidence.setMatchedFlowId(resolution.getMatchedFlowId());
        evidence.setMatchedRuleId(resolution.getMatchedRuleId());
        evidence.setSourceSystem(resolution.getSourceSystem());
        evidence.setResolutionMode(resolution.getResolutionMode());
        evidence.setRequiredOperations(resolution.getRequiredOperations());
        evidence.setRequiredCapabilities(resolution.getRequiredCapabilities());
        evidence.setSideEffectLevel(resolution.getSideEffectLevel());
        evidence.setCandidatePoolMode(resolution.getCandidatePoolMode());
        evidence.setRoutingStrategy(resolution.getRoutingStrategy());
        evidence.setExplicitActionAuthorizationRequired(resolution.isExplicitActionAuthorizationRequired());
        evidence.setDecisionStatus(resolution.getOutcome());
        evidence.setReasonCode(resolution.getReasonCode());
        evidence.setResolverVersion(resolution.getResolverVersion());
        evidence.setEvidence(resolution.getDetails());
        evidence.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        evidence.setCreatedBy("CANONICAL_DISPATCH_REQUIREMENT_RESOLVER");
        evidence.validate();
        return evidence;
    }

    private AgentCandidateScore toCandidateScore(GenericCandidateAgent candidate, TaskRequirementEvidence requirement,
                                                   GenericRoutingScore score, TaskAgentEligibilityShadowComparison eligibility) {
        AgentSnapshot runtime = candidate.getRuntime();
        Map<String,Object> breakdown = new LinkedHashMap<>(score.breakdown());
        breakdown.put("authoritativeCanonicalDispatch", true);
        breakdown.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        breakdown.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        breakdown.put("runtimeReportedCapabilitiesAuthority", false);
        breakdown.put("candidateOrigins", candidate.getOrigins().stream().map(Enum::name).toList());
        breakdown.put("eligibilityChecks", eligibility.getChecks().stream().map(check -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("evaluator", check.getEvaluatorCode());
            value.put("outcome", check.getOutcome() == null ? null : check.getOutcome().name());
            value.put("reasonCode", check.getReasonCode());
            return value;
        }).toList());
        return new AgentCandidateScore(candidate.getAgentId(),
                runtime == null ? null : runtime.getOwnerGatewayNodeId(),
                runtime == null ? null : runtime.getAgentSessionId(),
                runtime == null ? null : runtime.getSiteId(),
                runtime == null || runtime.getStatus() == null ? null : runtime.getStatus().name(),
                score.score(), requirement.getRequiredCapabilities(), eligibility.getBlockingReasonCodes(),
                "canonicalStrategy=" + requirement.getRoutingStrategy() + ", resolution=" + requirement.getResolutionMode(),
                Map.copyOf(breakdown));
    }

    private static String runtimeStatus(AgentSnapshot runtime) {
        return runtime == null || runtime.getStatus() == null ? null : runtime.getStatus().name();
    }

    private static String firstBlockingReason(
            List<GenericAuthoritativeRoutingResult.BlockedCandidateEvidence> blockedCandidates,
            String fallback) {
        if (blockedCandidates != null) {
            for (GenericAuthoritativeRoutingResult.BlockedCandidateEvidence blocked : blockedCandidates) {
                if (blocked == null || blocked.reasonCodes() == null) continue;
                for (String reason : blocked.reasonCodes()) {
                    if (!blank(reason)) return reason;
                }
            }
        }
        return fallback;
    }

    private static boolean isGovernedPoolTask(TaskRecord task) {
        return task != null
                && "A2A_POLICY_TO_AGENT_POOL".equals(normalize(task.getRoutingPath()))
                && !blank(task.getA2aPolicyId())
                && !blank(firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()));
    }

    private static List<String> normalized(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (!blank(normalized)) result.add(normalized);
            }
        }
        return result.stream().toList();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private static String require(String value, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null ? "-" : value.replace('\n',' ').replace('\r',' '); }
}
