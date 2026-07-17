package com.opensocket.aievent.core.routing.cutover;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.DispatchRequirementResolution;
import com.opensocket.aievent.core.routing.governance.DispatchRequirementResolver;
import com.opensocket.aievent.core.routing.governance.GenericRoutingStrategy;
import com.opensocket.aievent.core.routing.governance.RequirementDecisionStatus;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.routing.governance.eligibility.GenericDispatchEligibilityService;
import com.opensocket.aievent.core.routing.governance.eligibility.TaskAgentEligibilityShadowComparison;
import com.opensocket.aievent.core.routing.governance.routing.CandidateAgentProvider;
import com.opensocket.aievent.core.routing.governance.routing.GenericCandidateAgent;
import com.opensocket.aievent.core.routing.governance.routing.GenericRoutingScore;
import com.opensocket.aievent.core.routing.governance.routing.GenericRoutingScoreCalculator;
import com.opensocket.aievent.core.task.TaskRecord;

/** P11 authoritative dispatcher that composes generic requirement, eligibility, candidate-pool, and ranking contracts. */
@Service
public class GenericDispatchAuthoritativeService {
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
        this.flowRuleRoutingService=flowRuleRoutingService; this.requirementResolver=requirementResolver;
        this.candidateProvider=candidateProvider; this.eligibilityService=eligibilityService;
        this.scoreCalculator=scoreCalculator; this.properties=properties;
    }

    public GenericAuthoritativeRoutingResult route(TaskRecord task, Set<String> excludedAgentIds) {
        try {
            FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);
            if (plan == null || !plan.isMatched()) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.REQUIREMENT_BLOCKED,
                        null, List.of(), null, "FLOW_RULE_NOT_MATCHED", "No persisted Flow Rule matched the Task");
            }
            TaskRequirementEvidence requirement = toEvidence(requirementResolver.resolve(task, plan));
            if (requirement.getDecisionStatus() == RequirementDecisionStatus.BLOCKED) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.REQUIREMENT_BLOCKED,
                        requirement, List.of(), null, requirement.getReasonCode(), "Generic requirement resolution blocked the Task");
            }
            if (requirement.getRoutingStrategy() == GenericRoutingStrategy.MANUAL_REVIEW) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.MANUAL_REVIEW,
                        requirement, List.of(), null, "MANUAL_REVIEW_REQUIRED", "Flow routing strategy requires manual review");
            }
            Map<String, GenericCandidateAgent> pool = candidateProvider.provide(task, requirement, List.of());
            List<AgentCandidateScore> scores = new ArrayList<>();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Set<String> excluded = excludedAgentIds == null ? Set.of() : excludedAgentIds;
            for (Map.Entry<String, GenericCandidateAgent> entry : pool.entrySet()) {
                String agentId = entry.getKey();
                if (excluded.contains(agentId)) continue;
                AgentSnapshot runtime = entry.getValue().getRuntime();
                if (runtime != null && properties.isPoisonAgentExclusionEnabled()
                        && properties.getPoisonAgentFailureThreshold() > 0
                        && runtime.getRuntimeFailureCount() >= properties.getPoisonAgentFailureThreshold()) continue;
                TaskAgentEligibilityShadowComparison eligibility = eligibilityService.evaluateCandidateAuthoritatively(
                        task, requirement, runtime, agentId, now);
                if (!eligibility.isShadowEligible()) continue;
                GenericRoutingScore genericScore = scoreCalculator.score(requirement.getRoutingStrategy(), task, requirement, runtime);
                scores.add(toCandidateScore(entry.getValue(), requirement, genericScore, eligibility));
            }
            scores = scores.stream().sorted(Comparator.comparingInt(AgentCandidateScore::score).reversed()
                    .thenComparing(AgentCandidateScore::agentId)).toList();
            if (scores.isEmpty()) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.NO_CANDIDATE,
                        requirement, scores, null, "NO_GENERIC_ELIGIBLE_AGENT",
                        "No generic candidate passed all seven eligibility checks");
            }
            boolean thresholdApplies = scoreThresholdApplies(requirement);
            if (thresholdApplies && scores.getFirst().score() < properties.getMinimumScore()) {
                return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.NO_CANDIDATE,
                        requirement, scores, null, "GENERIC_SCORE_BELOW_MINIMUM",
                        "Highest eligible candidate score " + scores.getFirst().score()
                                + " is below minimum score " + properties.getMinimumScore());
            }
            return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.SELECTED,
                    requirement, scores, scores.getFirst(), "GENERIC_AGENT_SELECTED",
                    "P11 authoritative generic dispatch selected the highest ranked eligible Agent"
                            + (thresholdApplies ? " after minimum score validation"
                                    : "; score threshold bypassed for non-quality ranking strategy "
                                            + (requirement.getRoutingStrategy() == null ? "-" : requirement.getRoutingStrategy().name())));
        } catch (RuntimeException ex) {
            return new GenericAuthoritativeRoutingResult(GenericAuthoritativeRoutingResult.Status.ERROR,
                    null, List.of(), null, "GENERIC_ROUTING_ERROR", ex.getClass().getSimpleName() + ": " + safe(ex.getMessage()));
        }
    }

    private boolean scoreThresholdApplies(TaskRequirementEvidence requirement) {
        GenericRoutingStrategy strategy = requirement == null || requirement.getRoutingStrategy() == null
                ? GenericRoutingStrategy.WEIGHTED_SCORE
                : requirement.getRoutingStrategy();
        // ROUND_ROBIN produces a deterministic ordering value, not a quality score.
        // Applying the global minimum score here rejects otherwise eligible singleton
        // Flow Agent pools whenever the hash lands below the threshold.
        return strategy != GenericRoutingStrategy.ROUND_ROBIN;
    }


    private TaskRequirementEvidence toEvidence(DispatchRequirementResolution resolution) {
        if (resolution == null) {
            throw new IllegalStateException("Dispatch requirement resolution is required");
        }
        TaskRequirementEvidence evidence = new TaskRequirementEvidence();
        evidence.setTenantId(resolution.getTenantId());
        evidence.setEvidenceId("direct-req-" + UUID.randomUUID());
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
        evidence.setCreatedBy("DISPATCH_FLOW_DIRECT_RESOLVER");
        evidence.validate();
        return evidence;
    }
    private AgentCandidateScore toCandidateScore(GenericCandidateAgent candidate, TaskRequirementEvidence requirement,
                                                   GenericRoutingScore score, TaskAgentEligibilityShadowComparison eligibility) {
        AgentSnapshot runtime = candidate.getRuntime();
        Map<String,Object> breakdown = new LinkedHashMap<>(score.breakdown());
        breakdown.put("authoritativeGenericDispatch", true);
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
                "genericStrategy=" + requirement.getRoutingStrategy() + ", resolution=" + requirement.getResolutionMode(),
                Map.copyOf(breakdown));
    }
    private static String safe(String v){return v==null?"-":v.replace('\n',' ').replace('\r',' ');}
}
