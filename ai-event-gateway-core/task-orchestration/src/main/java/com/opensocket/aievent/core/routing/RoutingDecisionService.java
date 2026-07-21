package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2BlockingReason;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2Candidate;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2Response;
import com.opensocket.aievent.core.agent.skill.AgentDispatchSkillEvaluationService;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;
import com.opensocket.aievent.core.routing.legacy.GenericAuthorityBridge;
import com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator;
import com.opensocket.aievent.core.routing.flow.FlowResolution;
import com.opensocket.aievent.core.routing.flow.FlowResolver;
import com.opensocket.aievent.core.routing.evidence.RoutingBlockerResolver;
import com.opensocket.aievent.core.routing.evidence.RoutingEvidenceBuilder;
import com.opensocket.aievent.core.routing.pool.PoolResolver;
import com.opensocket.aievent.core.routing.selection.SelectionResult;
import com.opensocket.aievent.core.routing.selection.SelectionStrategyContext;
import com.opensocket.aievent.core.routing.selection.SelectionStrategyRegistry;
import com.opensocket.aievent.core.routing.scoring.CandidateScoringService;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation.LowCardinalityKeyNames;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverService;
import com.opensocket.aievent.core.routing.cutover.GenericDispatchAuthoritativeService;
import com.opensocket.aievent.core.task.TaskRecord;

@Service
public class RoutingDecisionService {
    private static final Logger log = LoggerFactory.getLogger(RoutingDecisionService.class);
    private final SelectionStrategyRegistry selectionStrategyRegistry = SelectionStrategyRegistry.createDefault();
    private final RoutingEvidenceBuilder routingEvidenceBuilder = new RoutingEvidenceBuilder();
    private final RoutingBlockerResolver routingBlockerResolver = new RoutingBlockerResolver();
    private final RuntimeEligibilityEvaluator runtimeEligibilityEvaluator;

    private final AgentDirectoryFacade agentDirectory;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RoutingProperties properties;
    private final ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private RoutingMetricsPort metrics;

    @Autowired(required = false)
    private AgentSkillRegistryService skillRegistryService;

    @Autowired(required = false)
    private AgentDispatchSkillEvaluationService dispatchSkillEvaluationService;




    @Autowired(required = false)
    private FlowRuleRoutingService flowRuleRoutingService;

    @Autowired(required = false)
    private AgentPoolRoutingRepository agentPoolRoutingRepository;

    @Autowired(required = false)
    private DispatchCutoverService dispatchCutoverService;

    @Autowired(required = false)
    private GenericDispatchAuthoritativeService genericAuthoritativeService;

    public RoutingDecisionService(AgentDirectoryFacade agentDirectory,
                                  RoutingDecisionRepository routingDecisionRepository,
                                  RoutingProperties properties) {
        this(agentDirectory, routingDecisionRepository, properties, ObservationRegistry.create());
    }

    @Autowired
    public RoutingDecisionService(AgentDirectoryFacade agentDirectory,
                                  RoutingDecisionRepository routingDecisionRepository,
                                  RoutingProperties properties,
                                  ObservationRegistry observationRegistry) {
        this.agentDirectory = agentDirectory;
        this.routingDecisionRepository = routingDecisionRepository;
        this.properties = properties;
        this.runtimeEligibilityEvaluator = new RuntimeEligibilityEvaluator(properties);
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.create() : observationRegistry;
    }

    public RoutingDecisionRecord decide(TaskRecord task) {
        return decide(task, Set.of());
    }

    public RoutingDecisionRecord decide(TaskRecord task, Set<String> excludedAgentIds) {
        Observation observation = RoutingObservationDocumentation.ASSIGNMENT_DECISION
                .observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.ASSIGNMENT_STATUS.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.BLOCKING_REASON_CODE.withValue("none"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TENANT_ID.withValue(valueOrNone(task == null ? null : task.getTenantId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(task == null ? null : task.getTaskId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.FLOW_ID.withValue(valueOrNone(task == null ? null : task.getMatchedFlowId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.RULE_ID.withValue(valueOrNone(task == null ? null : task.getMatchedRuleId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(task == null ? null : task.getRequestedSkill())));
        return observation.observe(() -> {
            try {
                RoutingDecisionRecord decision = routingOrchestrator().decide(task, excludedAgentIds);
                recordAssignmentDecision(observation, decision);
                return decision;
            } catch (RuntimeException ex) {
                low(observation, LowCardinalityKeyNames.RESULT, "error");
                low(observation, LowCardinalityKeyNames.ASSIGNMENT_STATUS, "error");
                low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "evaluation_error");
                throw ex;
            }
        });
    }

    RoutingProperties properties() {
        return properties;
    }

    RoutingEvidenceBuilder routingEvidenceBuilder() {
        return routingEvidenceBuilder;
    }

    RoutingBlockerResolver routingBlockerResolver() {
        return routingBlockerResolver;
    }

    private RoutingOrchestrator routingOrchestrator() {
        return new RoutingOrchestrator(this);
    }

    boolean isSourceFlowPoolFirstTask(TaskRecord task) {
        return flowResolver().isSourceFlowPoolFirstTask(task);
    }

    GenericAuthorityBridge genericAuthorityBridge() {
        return new GenericAuthorityBridge(properties, dispatchCutoverService, genericAuthoritativeService,
                routingEvidenceBuilder, this::saveAndRecord);
    }

    RoutingCandidateSelection selectCandidates(
            TaskRecord task,
            Set<String> excluded,
            RoutingPolicy policy,
            V2RoutingComparison v2Comparison,
            EligibilityEngineMode eligibilityMode) {
        Observation observation = RoutingObservationDocumentation.CANDIDATE_SELECTION
                .observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.CANDIDATE_RESULT.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.ROUTING_POLICY.withValue(normalizeObservationValue(policy == null ? null : policy.name())))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.ELIGIBILITY_MODE.withValue(normalizeObservationValue(eligibilityMode == null ? null : eligibilityMode.name())))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.BLOCKING_REASON_CODE.withValue("none"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TENANT_ID.withValue(valueOrNone(task == null ? null : task.getTenantId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(task == null ? null : task.getTaskId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.FLOW_ID.withValue(valueOrNone(task == null ? null : task.getMatchedFlowId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.RULE_ID.withValue(valueOrNone(task == null ? null : task.getMatchedRuleId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(task == null ? null : task.getRequestedSkill())));
        return observation.observe(() -> {
            CandidateFilterResult candidatePool = poolResolver().resolve(task, excluded, policy, isFlowRuleTask(task));
            if (isManualOnlyPool(candidatePool)) {
                low(observation, LowCardinalityKeyNames.CANDIDATE_RESULT, "manual_only");
                low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "manual_assignment_required");
                return new RoutingCandidateSelection(candidatePool, List.of());
            }
            SelectionStrategyContext selectionContext = selectionContext(candidatePool);
            CandidateScoringService candidateScoringService = candidateScoringService();
            boolean flowRuleTask = isFlowRuleTask(task);
            List<AgentCandidateScore> scores = candidatePool.included().stream()
                    .map(agent -> candidateScoringService.score(task, agent, policy, flowRuleTask))
                    .map(score -> selectionStrategyRegistry.annotate(score, selectionContext))
                    .map(score -> applyV2ScoreAnnotations(score, v2Comparison, eligibilityMode))
                    .toList();
            SelectionResult selectionResult = selectionStrategyRegistry.select(scores, selectionContext);
            scores = selectionResult.candidates();
            if (eligibilityMode.enforce() && v2Comparison.applied()) {
                scores = scores.stream()
                        .filter(score -> v2Comparison.v2EligibleAgentIds().contains(normalize(score.agentId())))
                        .sorted(Comparator.comparingInt(AgentCandidateScore::score).reversed())
                        .toList();
            }
            low(observation, LowCardinalityKeyNames.CANDIDATE_RESULT, scores.isEmpty() ? "no_candidate" : "candidates_available");
            low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, scores.isEmpty()
                    ? candidateBlockingReason(v2Comparison, eligibilityMode, candidatePool)
                    : "none");
            return new RoutingCandidateSelection(candidatePool, scores);
        });
    }

    private PoolResolver poolResolver() {
        return new PoolResolver(agentDirectory, agentPoolRoutingRepository, runtimeEligibilityEvaluator,
                selectionStrategyRegistry, properties.getMaxCandidates());
    }

    FlowResolver flowResolver() {
        return new FlowResolver(properties, flowRuleRoutingService);
    }

    boolean isManualOnlyPool(CandidateFilterResult pool) {
        return selectionStrategyRegistry.isManualOnly(selectionContext(pool));
    }

    private SelectionStrategyContext selectionContext(CandidateFilterResult pool) {
        if (pool == null) {
            return new SelectionStrategyContext(null, null, SelectionStrategyRegistry.LOWEST_LOAD, Map.of());
        }
        return new SelectionStrategyContext(pool.targetPoolId(), pool.targetPoolCode(), pool.selectionStrategy(), pool.membersByAgentId());
    }

    private CandidateScoringService candidateScoringService() {
        return new CandidateScoringService(properties, runtimeEligibilityEvaluator,
                skillRegistryService, dispatchSkillEvaluationService);
    }

    private V2RoutingComparison evaluateV2Routing(TaskRecord task, EligibilityEngineMode mode) {
        return V2RoutingComparison.notApplied(mode == null ? EligibilityEngineMode.SHADOW : mode);
    }

    private boolean shouldFailClosedBeforeLegacy(V2RoutingComparison comparison, EligibilityEngineMode mode) {
        if (mode == null || !mode.enforce() || comparison == null || !comparison.applied()) {
            return false;
        }
        return comparison.failed() || comparison.hasGlobalBlockingReasons() || comparison.v2EligibleAgentIds().isEmpty();
    }

    private AgentCandidateScore applyV2ScoreAnnotations(AgentCandidateScore score,
                                                        V2RoutingComparison comparison,
                                                        EligibilityEngineMode mode) {
        if (score == null || comparison == null || !comparison.applied()) {
            return score;
        }
        String agentId = normalize(score.agentId());
        DispatchEligibilityV2Candidate v2Candidate = comparison.candidateByAgentId().get(agentId);
        Map<String, Object> breakdown = new LinkedHashMap<>(score.scoreBreakdown() == null ? Map.of() : score.scoreBreakdown());
        breakdown.put("eligibilityEngineMode", mode == null ? null : mode.name());
        breakdown.put("eligibilityV2Applied", true);
        breakdown.put("eligibilityV2EligibleAgents", comparison.v2EligibleAgentIds());
        breakdown.put("eligibilityV2LegacyOnly", blank(score.agentId()) ? List.of() : comparison.legacyOnlyAgentIds(Set.of(score.agentId())));
        breakdown.put("eligibilityV2GlobalBlocking", comparison.globalReasonCodes());
        if (v2Candidate != null) {
            breakdown.put("eligibilityV2CandidateStatus", v2Candidate.getDispatchStatus());
            breakdown.put("eligibilityV2CandidateEligible", v2Candidate.isEligible());
            breakdown.put("eligibilityV2Score", v2Candidate.getScore());
            breakdown.put("eligibilityV2SupplyProfile", v2Candidate.getSupplyProfileCode());
            breakdown.put("eligibilityV2MatchedPolicies", v2Candidate.getMatchedPolicyCodes());
            breakdown.put("eligibilityV2MatchedCapabilities", v2Candidate.getMatchedCapabilities());
            breakdown.put("eligibilityV2MatchedRuntimeFeatures", v2Candidate.getMatchedRuntimeFeatures());
            breakdown.put("eligibilityV2BlockingReasons", v2Candidate.getBlockingReasons().stream().map(DispatchEligibilityV2BlockingReason::getCode).toList());
            breakdown.put("eligibilityV2ScoreBreakdown", v2Candidate.getScoreBreakdown());
        } else {
            breakdown.put("eligibilityV2CandidateStatus", "NOT_SEEN_BY_V2");
            breakdown.put("eligibilityV2CandidateEligible", false);
        }
        String reason = score.reason()
                + ", eligibilityV2=" + breakdown.get("eligibilityV2CandidateStatus")
                + (mode != null && mode.enforce() ? "(enforce)" : mode != null && mode == EligibilityEngineMode.WARN ? "(warn)" : "(shadow)");
        return new AgentCandidateScore(
                score.agentId(), score.ownerGatewayNodeId(), score.agentSessionId(), score.siteId(), score.status(),
                score.score(), score.matchedCapabilities(), score.missingCapabilities(), reason, immutableNullableMap(breakdown));
    }

    String v2DecisionSuffix(V2RoutingComparison comparison, EligibilityEngineMode mode, String selectedAgentId) {
        if (comparison == null || !comparison.applied()) {
            return "";
        }
        String normalized = normalize(selectedAgentId);
        String status = comparison.v2EligibleAgentIds().contains(normalized) ? "V2_ELIGIBLE" : "V2_NOT_ELIGIBLE";
        return "; P3-I eligibilityMode=" + (mode == null ? "SHADOW" : mode.name())
                + ", v2Status=" + status
                + ", v2Eligible=" + comparison.v2EligibleAgentIds().size()
                + ", v2Blocked=" + comparison.v2BlockedAgentIds().size();
    }

    DispatchUserFacingError userFacingV2EnforcementError(TaskRecord task, V2RoutingComparison comparison) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_ELIGIBILITY_V2_BLOCKED,
                "HIGH",
                "Eligibility V2 enforce mode blocked routing for this task.",
                "Check Dispatch Rules, Agent Dispatch Flow coverage, ACTIVE Runtime Binding, approved Admin-managed capabilities, trusted runtime features, and Quality Requirements.",
                "runbooks/dispatch/eligibility-v2-enforce",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", taskTypeCode(task),
                        "sourceSystem", task == null ? null : task.getSourceSystem()),
                details(
                        "engineMode", comparison == null ? null : comparison.mode().name(),
                        "evaluationFailed", comparison != null && comparison.failed(),
                        "evaluationError", comparison == null ? null : comparison.error(),
                        "globalBlockingReasons", comparison == null ? List.of() : comparison.globalReasonCodes(),
                        "v2EligibleAgents", comparison == null ? List.of() : comparison.v2EligibleAgentIds(),
                        "v2BlockedAgents", comparison == null ? List.of() : comparison.v2BlockedAgentIds()));
    }


    private boolean legacyProfileEligibilityDisabledFor(EligibilityEngineMode mode) {
        return mode != null && mode.enforce() && properties.isLegacyProfileEligibilityDisabledInEnforce();
    }

    boolean hasRequiredV2ScoreBreakdown(AgentCandidateScore score) {
        if (score == null || score.scoreBreakdown() == null) {
            return false;
        }
        Map<String, Object> breakdown = score.scoreBreakdown();
        return Boolean.TRUE.equals(breakdown.get("eligibilityV2Applied"))
                && breakdown.containsKey("eligibilityEngineMode")
                && breakdown.containsKey("eligibilityV2CandidateEligible")
                && breakdown.containsKey("eligibilityV2BlockingReasons")
                && breakdown.containsKey("eligibilityV2ScoreBreakdown")
                && breakdown.containsKey("eligibilityV2Score");
    }

    DispatchUserFacingError userFacingV2ScoreBreakdownRequiredError(TaskRecord task, List<String> missingAgentIds) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_ELIGIBILITY_V2_BLOCKED,
                "HIGH",
                "Eligibility V2 ENFORCE mode requires routing scoreBreakdown explainability.",
                "P3-O requires every selected ENFORCE candidate to retain eligibilityV2Applied, eligibilityV2BlockingReasons and eligibilityV2ScoreBreakdown. Re-run P3-N/P3-O acceptance and check routing decision persistence.",
                "runbooks/dispatch/eligibility-v2-enforce-scorebreakdown",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", taskTypeCode(task),
                        "sourceSystem", task == null ? null : task.getSourceSystem()),
                details(
                        "missingV2ScoreBreakdownAgents", missingAgentIds == null ? List.of() : missingAgentIds,
                        "requiredKeys", List.of("eligibilityV2Applied", "eligibilityEngineMode", "eligibilityV2CandidateEligible", "eligibilityV2BlockingReasons", "eligibilityV2ScoreBreakdown", "eligibilityV2Score")));
    }


    void applyUserFacingError(RoutingDecisionRecord decision, DispatchUserFacingError error) {
        decision.setUserFacingError(error);
        decision.setDecisionReason(error == null ? null : error.toLegacyDecisionReason());
    }

    private Map<String, Object> details(Object... keyValues) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) {
            return values;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                values.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        return values;
    }

    private String taskTypeCode(TaskRecord task) {
        return task == null ? null : normalize(task.getEffectiveTaskTypeCode());
    }


    private void recordAssignmentDecision(Observation observation, RoutingDecisionRecord decision) {
        if (decision == null) {
            low(observation, LowCardinalityKeyNames.RESULT, "error");
            low(observation, LowCardinalityKeyNames.ASSIGNMENT_STATUS, "none");
            low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "decision_missing");
            return;
        }
        String status = normalizeObservationValue(decision.getStatus() == null ? null : decision.getStatus().name());
        low(observation, LowCardinalityKeyNames.RESULT, routingResult(decision.getStatus()));
        low(observation, LowCardinalityKeyNames.ASSIGNMENT_STATUS, status);
        low(observation, LowCardinalityKeyNames.ROUTING_POLICY, normalizeObservationValue(decision.getRoutingPolicy() == null ? null : decision.getRoutingPolicy().name()));
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, routingBlockerResolver.observationBlockingReasonCode(decision));
        high(observation, HighCardinalityKeyNames.DECISION_ID, decision.getDecisionId());
        high(observation, HighCardinalityKeyNames.SELECTED_AGENT_ID, decision.getSelectedAgentId());
    }

    private String routingResult(RoutingDecisionStatus status) {
        if (status == null) return "error";
        return switch (status) {
            case SELECTED -> "selected";
            case NO_CANDIDATE -> "blocked";
            case MANUAL_REVIEW_REQUIRED -> "manual_review";
            case SUPPRESSED -> "suppressed";
        };
    }

    private String candidateBlockingReason(V2RoutingComparison comparison, EligibilityEngineMode mode, CandidateFilterResult pool) {
        boolean enforceGlobalReasons = mode != null && mode.enforce() && comparison != null && comparison.hasGlobalBlockingReasons();
        return runtimeEligibilityEvaluator.candidateBlockingReason(enforceGlobalReasons,
                comparison == null ? List.of() : comparison.globalReasonCodes(),
                pool);
    }

    private void low(Observation observation, LowCardinalityKeyNames key, String value) {
        if (observation != null && value != null && !value.isBlank()) {
            observation.lowCardinalityKeyValue(key.withValue(value));
        }
    }

    private void high(Observation observation, HighCardinalityKeyNames key, String value) {
        if (observation != null) {
            observation.highCardinalityKeyValue(key.withValue(valueOrNone(value)));
        }
    }

    private String normalizeObservationValue(String value) {
        return value == null || value.isBlank() ? "none" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    String flowRuleDecisionSuffix(TaskRecord task) {
        return flowResolver().decisionSuffix(task);
    }

    RoutingDecisionRecord saveAndRecord(RoutingDecisionRecord decision) {
        RoutingDecisionRecord saved = routingDecisionRepository.save(decision);
        log.debug("routing_decision_saved decisionId={} taskId={} status={} selectedAgentId={} policy={} reason={}",
                saved.getDecisionId(), saved.getTaskId(), saved.getStatus(), saved.getSelectedAgentId(), saved.getRoutingPolicy(), saved.getDecisionReason());
        if (metrics != null) {
            metrics.recordRoutingDecision(saved);
        }
        return saved;
    }

    boolean requiresManualReview(RoutingPolicy policy) {
        return policy == RoutingPolicy.MANUAL_REVIEW;
    }

    private RoutingPolicy parsePolicyOrDefault(String raw, RoutingPolicy fallback) {
        RoutingPolicy parsed = parsePolicyOrNull(raw);
        return parsed == null ? fallback : parsed;
    }

    private RoutingPolicy parsePolicyOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = normalize(raw);
        try {
            return RoutingPolicy.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            // Historical policy names are intentionally opaque in P5.  Existing work may recover
            // only from saved retry/capability evidence and receives the generic recovery policy;
            // new work with an unknown policy remains fail-closed.
            return null;
        }
    }

    boolean isFlowRuleTask(TaskRecord task) {
        return flowResolver().isFlowRuleTask(task);
    }

    Set<String> normalizeAgentIds(Set<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String agentId : agentIds) {
            if (agentId != null && !agentId.isBlank()) {
                normalized.add(agentId);
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Map.copyOf rejects null values. Routing score diagnostics intentionally keep optional
     * fields such as skillReason / eligibility reason even when they are absent, so use an
     * unmodifiable LinkedHashMap copy that preserves null diagnostic values instead of
     * crashing assignment before dispatch delivery.
     */
    private Map<String, Object> immutableNullableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record RoutingCandidateSelection(CandidateFilterResult candidatePool, List<AgentCandidateScore> scores) { }

    record V2RoutingComparison(
            EligibilityEngineMode mode,
            boolean applied,
            boolean failed,
            String error,
            DispatchEligibilityV2Response response,
            Set<String> v2EligibleAgentIds,
            Set<String> v2BlockedAgentIds,
            Map<String, DispatchEligibilityV2Candidate> candidateByAgentId,
            List<DispatchEligibilityV2BlockingReason> globalReasons) {
        static V2RoutingComparison notApplied(EligibilityEngineMode mode) {
            return new V2RoutingComparison(mode, false, false, null, null, Set.of(), Set.of(), Map.of(), List.of());
        }
        static V2RoutingComparison failed(EligibilityEngineMode mode, String error) {
            return new V2RoutingComparison(mode, true, true, error, null, Set.of(), Set.of(), Map.of(), List.of());
        }
        static V2RoutingComparison from(EligibilityEngineMode mode, DispatchEligibilityV2Response response) {
            LinkedHashSet<String> eligible = new LinkedHashSet<>();
            LinkedHashSet<String> blocked = new LinkedHashSet<>();
            LinkedHashMap<String, DispatchEligibilityV2Candidate> candidates = new LinkedHashMap<>();
            if (response != null) {
                for (DispatchEligibilityV2Candidate candidate : response.getEligibleCandidates()) {
                    if (candidate != null && candidate.getAgentId() != null && !candidate.getAgentId().isBlank()) {
                        String agentId = candidate.getAgentId().trim().toUpperCase(Locale.ROOT);
                        eligible.add(agentId);
                        candidates.putIfAbsent(agentId, candidate);
                    }
                }
                for (DispatchEligibilityV2Candidate candidate : response.getBlockedCandidates()) {
                    if (candidate != null && candidate.getAgentId() != null && !candidate.getAgentId().isBlank()) {
                        String agentId = candidate.getAgentId().trim().toUpperCase(Locale.ROOT);
                        blocked.add(agentId);
                        candidates.putIfAbsent(agentId, candidate);
                    }
                }
            }
            return new V2RoutingComparison(mode, true, false, null, response, Set.copyOf(eligible), Set.copyOf(blocked), Map.copyOf(candidates),
                    response == null ? List.of() : response.getGlobalBlockingReasons());
        }
        boolean hasGlobalBlockingReasons() {
            return globalReasons != null && globalReasons.stream().anyMatch(reason -> "BLOCKING".equalsIgnoreCase(reason.getSeverity()));
        }
        List<String> globalReasonCodes() {
            return globalReasons == null ? List.of() : globalReasons.stream().map(DispatchEligibilityV2BlockingReason::getCode).filter(code -> code != null && !code.isBlank()).toList();
        }
        List<String> legacyOnlyAgentIds(Set<String> legacyAgentIds) {
            if (legacyAgentIds == null || legacyAgentIds.isEmpty()) return List.of();
            return legacyAgentIds.stream()
                    .filter(agentId -> agentId != null && !agentId.isBlank())
                    .map(agentId -> agentId.trim().toUpperCase(Locale.ROOT))
                    .filter(agentId -> !v2EligibleAgentIds.contains(agentId))
                    .toList();
        }
    }
}
