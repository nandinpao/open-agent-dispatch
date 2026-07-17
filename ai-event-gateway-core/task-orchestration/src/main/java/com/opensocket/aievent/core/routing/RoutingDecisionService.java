package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2BlockingReason;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2Candidate;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityV2Response;
import com.opensocket.aievent.core.agent.eligibility.TaskDispatchRequirements;
import com.opensocket.aievent.core.agent.skill.AgentDispatchSkillEvaluationService;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationRequest;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverDecision;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.routing.observation.RoutingObservationDocumentation.LowCardinalityKeyNames;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverService;
import com.opensocket.aievent.core.routing.cutover.GenericAuthoritativeRoutingResult;
import com.opensocket.aievent.core.routing.cutover.GenericDispatchAuthoritativeService;
import com.opensocket.aievent.core.task.TaskRecord;

@Service
public class RoutingDecisionService {
    private static final Logger log = LoggerFactory.getLogger(RoutingDecisionService.class);

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
                RoutingDecisionRecord decision = decideObserved(task, excludedAgentIds);
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

    private RoutingDecisionRecord decideObserved(TaskRecord task, Set<String> excludedAgentIds) {
        Set<String> excluded = normalizeAgentIds(excludedAgentIds);
        task = applyFlowRuleRuntimeRepair(task);
        RoutingPolicy policy = resolvePolicy(task);
        log.info("routing_decision_started taskId={} incidentId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} matchedFlowId={} matchedRuleId={} routingPath={} targetPoolId={} assignedPoolId={} policy={} excludedAgents={} phase32PoolFirst=true",
                task == null ? null : task.getTaskId(), task == null ? null : task.getIncidentId(), task == null ? null : task.getTenantId(),
                task == null ? null : task.getSourceSystem(), task == null ? null : task.getEventStage(), task == null ? null : task.getObjectType(),
                task == null ? null : task.getEventType(), task == null ? null : task.getErrorCode(), task == null ? null : task.getClassificationStatus(),
                task == null ? null : task.getMatchedFlowId(), task == null ? null : task.getMatchedRuleId(), task == null ? null : task.getRoutingPath(),
                task == null ? null : task.getTargetPoolId(), task == null ? null : task.getAssignedPoolId(), policy, excluded.size());
        RoutingDecisionRecord decision = new RoutingDecisionRecord();
        decision.setDecisionId("route-" + UUID.randomUUID());
        decision.setTaskId(task.getTaskId());
        decision.setIncidentId(task.getIncidentId());
        decision.setRoutingPolicy(policy);
        decision.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (!properties.isAssignmentEnabled()) {
            decision.setStatus(RoutingDecisionStatus.SUPPRESSED);
            decision.setDecisionReason("Assignment routing is disabled by ROUTING_ASSIGNMENT_ENABLED=false");
            log.info("routing_decision_suppressed taskId={} policy={} reason={}", task.getTaskId(), policy, decision.getDecisionReason());
            return saveAndRecord(decision);
        }
        if (properties.isZeroSpecialCaseRuntimeEnabled()
                && properties.isFlowRuleRoutingEnabled() && !properties.isFlowRuleLegacyFallbackEnabled()
                && !isFlowRuleTask(task)) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("NO_ACTIVE_FLOW_RULE: new work requires a matched Dispatch Flow Rule; legacy profile/source fallback is disabled");
            log.warn("routing_new_work_fail_closed_no_flow_rule taskId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} reason=NO_ACTIVE_FLOW_RULE legacyFallback=false",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode());
            return saveAndRecord(decision);
        }
        if (!isPhase32PoolFirstTask(task)) {
            RoutingDecisionRecord genericDecision = decideWithGenericAuthority(task, excluded, decision);
            if (genericDecision != null) {
                return genericDecision;
            }
        } else {
            log.info("routing_phase32_pool_first_bypassed_generic_authority taskId={} tenantId={} sourceSystem={} routingPath={} matchedFlowId={} matchedRuleId={} targetPoolId={} reason=SOURCE_FLOW_POOL_IS_AUTHORITATIVE phase32PoolFirst=true",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getRoutingPath(),
                    task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId());
        }
        if (requiresManualReview(policy)) {
            decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
            decision.setDecisionReason("Routing policy " + policy + " requires human review before assignment");
            log.info("routing_decision_manual_review taskId={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} requestedSkill={}",
                    task.getTaskId(), policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill());
            return saveAndRecord(decision);
        }

        // Phase 32-D: standard runtime authority is Source Flow -> Rule/default Pool ->
        // Agent Pool member -> Agent runtime/capacity. Capability is metadata only and
        // does not participate as a routing gate in the first Pool-first model.
        EligibilityEngineMode eligibilityMode = EligibilityEngineMode.SHADOW;
        V2RoutingComparison v2Comparison = V2RoutingComparison.notApplied(eligibilityMode);

        RoutingCandidateSelection candidateSelection = selectCandidates(task, excluded, policy, v2Comparison, eligibilityMode);
        CandidateFilterResult candidatePool = candidateSelection.candidatePool();
        List<AgentCandidateScore> scores = candidateSelection.scores();
        decision.setCandidates(scores);
        if (scores.isEmpty()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            applyUserFacingError(decision, eligibilityMode.enforce() && v2Comparison.applied()
                    ? userFacingV2EnforcementError(task, v2Comparison)
                    : userFacingNoCandidateError(task, candidatePool));
            log.warn("routing_no_candidate taskId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} poolMemberCount={} eligibleAgentCount={} poolBlocker={} reservationExcluded={} poisonExcluded={} userFacingErrorCode={} phase32PoolFirst=true",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode(), task.getClassificationStatus(),
                    policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                    candidatePool == null ? task.getTargetPoolId() : candidatePool.targetPoolId(), candidatePool == null ? null : candidatePool.targetPoolCode(),
                    candidatePool == null ? 0 : candidatePool.memberCount(), candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                    candidatePool == null ? null : candidatePool.poolBlockerCode(),
                    candidatePool == null ? Set.of() : candidatePool.reservationExcluded(), candidatePool == null ? Set.of() : candidatePool.poisonExcluded(),
                    decision.getUserFacingError() == null ? null : decision.getUserFacingError().getCode());
            return saveAndRecord(decision);
        }
        if (eligibilityMode.enforce() && properties.isRequireV2ScoreBreakdownInEnforce()) {
            List<String> missingV2Explainability = scores.stream()
                    .filter(score -> !hasRequiredV2ScoreBreakdown(score))
                    .map(AgentCandidateScore::agentId)
                    .toList();
            if (!missingV2Explainability.isEmpty()) {
                decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
                applyUserFacingError(decision, userFacingV2ScoreBreakdownRequiredError(task, missingV2Explainability));
                return saveAndRecord(decision);
            }
        }
        AgentCandidateScore selected = scores.getFirst();
        if (selected.score() < properties.getMinimumScore()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            applyUserFacingError(decision, userFacingBelowMinimumError(task, selected, candidatePool));
            log.warn("routing_below_minimum taskId={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} requestedSkill={} selectedAgentId={} selectedScore={} minimumScore={} missingCapabilities={}",
                    task.getTaskId(), policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(),
                    selected.agentId(), selected.score(), properties.getMinimumScore(), selected.missingCapabilities());
            return saveAndRecord(decision);
        }
        decision.setStatus(RoutingDecisionStatus.SELECTED);
        decision.setSelectedAgentId(selected.agentId());
        decision.setSelectedGatewayNodeId(selected.ownerGatewayNodeId());
        decision.setSelectedAgentSessionId(selected.agentSessionId());
        decision.setSelectedSiteId(selected.siteId());
        decision.setSelectedScore(selected.score());
        decision.setDecisionReason("Selected by " + policy + ": " + selected.reason() + candidatePool.diagnostics()
                + flowRuleDecisionSuffix(task)
                + v2DecisionSuffix(v2Comparison, eligibilityMode, selected.agentId()));
        log.info("routing_selected taskId={} decisionId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} poolMemberCount={} eligibleAgentCount={} selectedAgentId={} selectedScore={} candidateCount={} phase32PoolFirst=true",
                task.getTaskId(), decision.getDecisionId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode(), task.getClassificationStatus(),
                policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                candidatePool == null ? task.getTargetPoolId() : candidatePool.targetPoolId(), candidatePool == null ? null : candidatePool.targetPoolCode(),
                candidatePool == null ? 0 : candidatePool.memberCount(), candidatePool == null ? scores.size() : candidatePool.eligibleAgentCount(),
                selected.agentId(), selected.score(), scores.size());
        return saveAndRecord(decision);
    }

    private boolean isPhase32PoolFirstTask(TaskRecord task) {
        if (task == null || !isFlowRuleTask(task)) {
            return false;
        }
        String path = normalize(task.getRoutingPath());
        return !blank(task.getTargetPoolId())
                || !blank(task.getAssignedPoolId())
                || "SOURCE_FLOW_DEFAULT_POOL".equals(path)
                || "SOURCE_FLOW_POOL".equals(path)
                || "SOURCE_DEFAULT".equals(normalize(task.getMatchedRuleId()));
    }

    private RoutingDecisionRecord decideWithGenericAuthority(
            TaskRecord task, Set<String> excluded, RoutingDecisionRecord decision) {
        if (task == null || !isFlowRuleTask(task)) {
            return null;
        }
        if (genericAuthoritativeService == null || dispatchCutoverService == null
                || !properties.isGenericAuthoritativeEnabled()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("P11 generic authority is required for new Flow work and is unavailable");
            return saveAndRecord(decision);
        }
        DispatchCutoverDecision cutover;
        try {
            cutover = dispatchCutoverService.decide(task);
        } catch (RuntimeException ex) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("P11 cutover decision failed closed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return saveAndRecord(decision);
        }
        if (!cutover.isAuthoritative()) {
            decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
            decision.setRoutingPolicy(RoutingPolicy.MANUAL_REVIEW);
            decision.setDecisionReason("P11 legacy control path is decommissioned; non-authoritative cutover state requires explicit operator review: "
                    + cutover.getReasonCode());
            dispatchCutoverService.recordOutcome(task, cutover, true, true, null, null,
                    "LEGACY_CONTROL_PATH_DECOMMISSIONED");
            log.warn("generic_dispatch_non_authoritative_held taskId={} flowId={} mode={} bucket={} reason={} legacyControlPath=false",
                    task.getTaskId(), task.getMatchedFlowId(), cutover.getConfiguredMode(),
                    cutover.getDeterministicBucket(), cutover.getReasonCode());
            return saveAndRecord(decision);
        }
        GenericAuthoritativeRoutingResult result = genericAuthoritativeService.route(task, excluded);
        decision.setRoutingPolicy(RoutingPolicy.FLOW_RULE);
        decision.setCandidates(result.candidates());
        switch (result.status()) {
            case SELECTED -> {
                AgentCandidateScore selected = result.selected();
                decision.setStatus(RoutingDecisionStatus.SELECTED);
                decision.setSelectedAgentId(selected.agentId());
                decision.setSelectedGatewayNodeId(selected.ownerGatewayNodeId());
                decision.setSelectedAgentSessionId(selected.agentSessionId());
                decision.setSelectedSiteId(selected.siteId());
                decision.setSelectedScore(selected.score());
                decision.setDecisionReason("P11 generic authority: " + result.reason()
                        + "; cutoverMode=" + cutover.getConfiguredMode()
                        + "; bucket=" + cutover.getDeterministicBucket());
            }
            case MANUAL_REVIEW -> {
                decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
                decision.setDecisionReason("P11 generic authority requires manual review: " + result.reasonCode());
            }
            case REQUIREMENT_BLOCKED, NO_CANDIDATE, ERROR -> {
                decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
                decision.setDecisionReason("P11 generic authority fail-closed: " + result.reasonCode() + ": " + result.reason());
            }
        }
        dispatchCutoverService.recordOutcome(task, cutover, result.requirementBlocked(), result.noCandidate(),
                result.selected() == null ? null : result.selected().agentId(), null, result.reasonCode());
        List<Map<String, Object>> candidateTrace = result.candidates().stream().map(candidate -> {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("agentId", candidate.agentId());
            trace.put("score", candidate.score());
            trace.put("status", candidate.status());
            trace.put("matchedCapabilities", candidate.matchedCapabilities());
            trace.put("missingCapabilities", candidate.missingCapabilities());
            trace.put("reason", candidate.reason());
            trace.put("scoreBreakdown", candidate.scoreBreakdown());
            return trace;
        }).toList();
        if (result.selected() == null) {
            log.warn("generic_dispatch_authoritative_no_selection tenantId={} taskId={} flowId={} ruleId={} sourceSystem={} status={} reasonCode={} reason={} candidateCount={} candidates={} cutoverMode={} bucket={} directLegacyFallback=false",
                    task.getTenantId(), task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getSourceSystem(),
                    result.status(), result.reasonCode(), result.reason(), result.candidates().size(), candidateTrace,
                    cutover.getConfiguredMode(), cutover.getDeterministicBucket());
        } else {
            log.info("generic_dispatch_authoritative_completed tenantId={} taskId={} flowId={} ruleId={} sourceSystem={} status={} selectedAgentId={} selectedScore={} reasonCode={} candidateCount={} candidates={} cutoverMode={} bucket={} directLegacyFallback=false",
                    task.getTenantId(), task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getSourceSystem(),
                    result.status(), result.selected().agentId(), result.selected().score(), result.reasonCode(),
                    result.candidates().size(), candidateTrace, cutover.getConfiguredMode(), cutover.getDeterministicBucket());
        }
        return saveAndRecord(decision);
    }

    private TaskRecord applyFlowRuleRuntimeRepair(TaskRecord task) {
        // R12.11: reassign/retry may route an existing legacy-created Task whose
        // matchedFlowId/matchedRuleId/requestedSkill columns were still empty.  Do
        // not send that task back to inferred policy / Dispatch Flow Agent Assignment gates.  Resolve
        // the Flow-owned rule at routing time and mutate the in-memory TaskRecord so
        // the rest of the assignment pipeline scores it as FLOW_RULE.  Successful
        // assignments persist the repaired evidence through TaskAssignmentService.
        if (task == null || isFlowRuleTask(task) || !properties.isFlowRuleRoutingEnabled() || flowRuleRoutingService == null) {
            return task;
        }
        try {
            FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);
            if (plan == null || !plan.isMatched()) {
                log.warn("routing_flow_rule_runtime_repair_not_matched taskId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} reason={}",
                        task.getTaskId(), task.getSourceSystem(), task.getEventStage(), task.getEventType(), task.getObjectType(), task.getErrorCode(),
                        plan == null ? "NO_PLAN" : plan.getReason());
                return task;
            }
            task.setMatchedFlowId(plan.getFlowId());
            task.setMatchedRuleId(plan.getRuleId());
            task.setRequestedSkill(plan.getRequestedSkill());
            task.setEventStage(firstNonBlank(plan.getEventStage(), task.getEventStage(), "EXTERNAL"));
            task.setTargetSystem(firstNonBlank(plan.getTargetSystem(), task.getTargetSystem()));
            task.setHandoffMode(firstNonBlank(plan.getHandoffMode(), task.getHandoffMode(), "DIRECT_ASSIGN"));
            task.setTargetPoolId(firstNonBlank(plan.getTargetPoolId(), task.getTargetPoolId()));
            task.setAssignedPoolId(firstNonBlank(plan.getTargetPoolId(), task.getAssignedPoolId()));
            task.setRoutingPath(firstNonBlank(plan.getRoutingPath(), plan.isSourceDefaultPool() ? "SOURCE_FLOW_DEFAULT_POOL" : "FLOW_RULE"));
            task.setRoutingPolicy("FLOW_RULE");
            task.setRequiredCapabilities(List.of());
            log.info("routing_flow_rule_runtime_repaired taskId={} matchedFlowId={} matchedRuleId={} targetPoolId={} sourceDefaultPool={} eventStage={} routingPath={}",
                    task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId(), plan.isSourceDefaultPool(), task.getEventStage(), task.getRoutingPath());
            return task;
        } catch (Exception ex) {
            log.warn("routing_flow_rule_runtime_repair_failed taskId={} reason={}: {}",
                    task.getTaskId(), ex.getClass().getSimpleName(), ex.getMessage());
            return task;
        }
    }


    private RoutingCandidateSelection selectCandidates(
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
            CandidateFilterResult candidatePool = resolveCandidatePool(task, excluded, policy);
            List<AgentCandidateScore> scores = candidatePool.included().stream()
                    .map(agent -> score(task, agent, policy))
                    .map(score -> annotatePoolScore(score, candidatePool))
                    .map(score -> applyV2ScoreAnnotations(score, v2Comparison, eligibilityMode))
                    .sorted(poolComparator(candidatePool))
                    .toList();
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

    private CandidateFilterResult resolveCandidatePool(TaskRecord task, Set<String> excluded, RoutingPolicy policy) {
        String targetPoolId = task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId());
        if (isFlowRuleTask(task) && blank(targetPoolId)) {
            return CandidateFilterResult.blocked(null, null, "SOURCE_FLOW_HAS_NO_DEFAULT_POOL", excluded);
        }
        if (!blank(targetPoolId)) {
            if (agentPoolRoutingRepository == null) {
                return CandidateFilterResult.blocked(targetPoolId, null, "AGENT_POOL_REPOSITORY_UNAVAILABLE", excluded);
            }
            AgentPoolRoutingSnapshot pool = agentPoolRoutingRepository.findActivePool(task.getTenantId(), targetPoolId).orElse(null);
            if (pool == null) {
                log.warn("routing_pool_blocker tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} blockerCode=RULE_TARGET_POOL_NOT_FOUND phase32PoolFirst=true",
                        task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), targetPoolId);
                return CandidateFilterResult.blocked(targetPoolId, null, "RULE_TARGET_POOL_NOT_FOUND", excluded);
            }
            if (pool.getMembers() == null || pool.getMembers().isEmpty()) {
                log.warn("routing_pool_blocker tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} blockerCode=POOL_HAS_NO_ACTIVE_MEMBER phase32PoolFirst=true",
                        task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), pool.getPoolId(), pool.getPoolCode());
                return CandidateFilterResult.blocked(pool.getPoolId(), pool.getPoolCode(), "POOL_HAS_NO_ACTIVE_MEMBER", excluded);
            }
            Map<String, AgentPoolRoutingMember> membersByAgentId = new LinkedHashMap<>();
            List<AgentSnapshot> candidates = new ArrayList<>();
            for (AgentPoolRoutingMember member : pool.getMembers()) {
                if (member == null || blank(member.getAgentId())) {
                    continue;
                }
                membersByAgentId.putIfAbsent(normalize(member.getAgentId()), member);
                agentDirectory.findById(member.getAgentId()).ifPresent(candidates::add);
            }
            CandidateFilterResult filtered = filterCandidates(candidates, excluded);
            String poolBlocker = filtered.included().isEmpty() ? poolBlocker(pool, candidates, filtered) : null;
            log.info("routing_pool_snapshot tenantId={} taskId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} selectionStrategy={} poolMemberCount={} runtimeCandidateCount={} eligibleAgentCount={} blockerCode={} phase32PoolFirst=true",
                    task.getTenantId(), task.getTaskId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                    pool.getPoolId(), pool.getPoolCode(), pool.getSelectionStrategy(), pool.getMembers().size(), candidates.size(), filtered.included().size(), poolBlocker);
            return filtered.withPool(pool.getPoolId(), pool.getPoolCode(), pool.getSelectionStrategy(), membersByAgentId, poolBlocker);
        }
        AgentQuery query = queryFor(task, properties.getMaxCandidates(), requiresLocalSearch(policy), false);
        List<AgentSnapshot> candidates = new ArrayList<>(agentDirectory.findCandidates(query));
        if (allowsGlobalFallback(policy) && candidates.stream().noneMatch(agent -> includeForScoring(agent, excluded) && agent.isAssignable())) {
            candidates.addAll(agentDirectory.findCandidates(queryFor(task, properties.getMaxCandidates(), false, false)));
        }
        return filterCandidates(candidates, excluded);
    }

    private String poolBlocker(AgentPoolRoutingSnapshot pool, List<AgentSnapshot> rawCandidates, CandidateFilterResult filtered) {
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

    private Comparator<AgentCandidateScore> poolComparator(CandidateFilterResult pool) {
        String strategy = normalize(pool == null ? null : pool.selectionStrategy());
        Comparator<AgentCandidateScore> base = Comparator.comparingInt(AgentCandidateScore::score).reversed();
        if ("LOWEST_LOAD".equals(strategy)) {
            return Comparator.<AgentCandidateScore>comparingInt(score -> intBreakdown(score.scoreBreakdown(), "effectiveTaskCount"))
                    .thenComparing(base);
        }
        if ("WEIGHTED_SCORE".equals(strategy)) {
            return base.thenComparing((left, right) -> Integer.compare(poolMemberWeight(pool, right.agentId()), poolMemberWeight(pool, left.agentId())));
        }
        return base;
    }

    private AgentCandidateScore annotatePoolScore(AgentCandidateScore score, CandidateFilterResult pool) {
        if (score == null || pool == null || blank(pool.targetPoolId())) {
            return score;
        }
        Map<String, Object> breakdown = new LinkedHashMap<>(score.scoreBreakdown() == null ? Map.of() : score.scoreBreakdown());
        breakdown.put("targetPoolId", pool.targetPoolId());
        breakdown.put("targetPoolCode", firstNonBlank(pool.targetPoolCode(), ""));
        breakdown.put("poolSelectionStrategy", firstNonBlank(pool.selectionStrategy(), "LOWEST_LOAD"));
        breakdown.put("poolMemberPriority", poolMemberPriority(pool, score.agentId()));
        breakdown.put("poolMemberWeight", poolMemberWeight(pool, score.agentId()));
        breakdown.put("effectiveTaskCount", effectiveTaskCountBreakdown(score));
        return new AgentCandidateScore(score.agentId(), score.ownerGatewayNodeId(), score.agentSessionId(), score.siteId(), score.status(),
                score.score(), score.matchedCapabilities(), score.missingCapabilities(),
                score.reason() + ", targetPool=" + pool.targetPoolId(), immutableNullableMap(breakdown));
    }

    private int effectiveTaskCountBreakdown(AgentCandidateScore score) {
        Object runtime = score == null || score.scoreBreakdown() == null ? null : score.scoreBreakdown().get("runtime");
        if (runtime instanceof Map<?, ?> map) {
            Number current = numberValue(map.get("currentTaskCount"));
            Number reserved = numberValue(map.get("reservedTaskCount"));
            return (current == null ? 0 : current.intValue()) + (reserved == null ? 0 : reserved.intValue());
        }
        return 0;
    }

    private int intBreakdown(Map<String, Object> breakdown, String key) {
        Number number = numberValue(breakdown == null ? null : breakdown.get(key));
        return number == null ? 0 : number.intValue();
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) return number;
        if (value instanceof String text && !text.isBlank()) {
            try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private int poolMemberPriority(CandidateFilterResult pool, String agentId) {
        AgentPoolRoutingMember member = poolMember(pool, agentId);
        return member == null ? 100 : member.getPriority();
    }

    private int poolMemberWeight(CandidateFilterResult pool, String agentId) {
        AgentPoolRoutingMember member = poolMember(pool, agentId);
        return member == null ? 1 : member.getWeight();
    }

    private AgentPoolRoutingMember poolMember(CandidateFilterResult pool, String agentId) {
        if (pool == null || pool.membersByAgentId() == null || blank(agentId)) {
            return null;
        }
        return pool.membersByAgentId().get(normalize(agentId));
    }

    private CandidateFilterResult filterCandidates(List<AgentSnapshot> candidates, Set<String> excludedAgentIds) {
        if (candidates == null || candidates.isEmpty()) {
            return new CandidateFilterResult(List.of(), excludedAgentIds == null ? Set.of() : excludedAgentIds, Set.of(), null, null, null, Map.of(), null);
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

    private boolean includeForScoring(AgentSnapshot agent, Set<String> excluded) {
        return agent != null
                && agent.getAgentId() != null
                && !agent.getAgentId().isBlank()
                && (excluded == null || !excluded.contains(agent.getAgentId()))
                && !isPoisonExcluded(agent);
    }

    private boolean isPoisonExcluded(AgentSnapshot agent) {
        if (!properties.isPoisonAgentExclusionEnabled() || agent == null) {
            return false;
        }
        int threshold = properties.getPoisonAgentFailureThreshold();
        return threshold > 0 && agent.getRuntimeFailureCount() >= threshold;
    }

    private AgentQuery queryFor(TaskRecord task, int limit, boolean local, boolean assignableOnly) {
        AgentQuery query = new AgentQuery();
        query.setAssignableOnly(assignableOnly);
        // Capability authority is Admin UI/Core governed assignment, not the runtime
        // AgentSnapshot capabilities_json/capability_profile_json.  Do not pass business
        // capabilities into AgentDirectory.search(), because that repository can only see
        // runtime observations and would incorrectly remove perfectly eligible Admin-managed
        // Agents before backend eligibility/scoring can evaluate them.
        query.setRequiredCapabilities(List.of());
        query.setLimit(limit);
        if (local) {
            query.setSiteId(task.getSiteId());
        }
        return query;
    }

    private AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy) {
        BackendEligibilityScore backendEligibility = evaluateBackendEligibility(task, agent);
        List<String> requiredCapabilities = routingRequiredCapabilities(task);
        List<String> effectiveCapabilitiesWork = effectiveCapabilities(agent);
        if (backendEligibility.applied() && !backendEligibility.blockingFailure()) {
            // P3-AC: Backend eligibility is the authority for Admin-managed Dispatch Flow coverage grants.
            // If an Agent has the required Dispatch Flow coverage, the scope's capability bindings should
            // be considered effective for scoring even when the legacy/runtime capability snapshot
            // does not contain those business capabilities.
            effectiveCapabilitiesWork = mergeDistinct(effectiveCapabilitiesWork, requiredCapabilities);
        }
        final List<String> effectiveCapabilities = effectiveCapabilitiesWork;
        List<String> matched = requiredCapabilities.stream()
                .filter(effectiveCapabilities::contains)
                .toList();
        List<String> missing = requiredCapabilities.stream()
                .filter(cap -> !effectiveCapabilities.contains(cap))
                .toList();
        int capabilityScore = requiredCapabilities.isEmpty()
                ? 40
                : (int) Math.round(40.0 * matched.size() / requiredCapabilities.size());
        boolean runtimeAssignable = agent.isAssignable();
        boolean runtimeCapacityFull = isCapacityFull(agent);
        int availabilityScore = runtimeAssignable ? 20 : 0;
        int slotScore = Math.min(10, Math.max(0, agent.getAvailableSlots()) * 5);
        int loadScore = runtimeLoadScore(agent, policy);
        int siteScore = task.getSiteId() != null && task.getSiteId().equals(agent.getSiteId()) ? 10 : 0;
        int healthScore = Math.round(agent.getHealthScore() / 10.0f);
        int policyBonus = policyBonus(policy, missing, siteScore);
        int penalty = runtimePenalty(agent);

        SkillScore skill = evaluateSkillAware(task, agent);
        SkillVersionScore skillVersion = evaluateSkillVersionCompatibility(task, agent);
        int backendEligibilityScore = backendEligibility.score();
        int backendEligibilityPenalty = backendEligibility.penalty();
        int skillScore = skill.score();
        int skillPenalty = skill.penalty();
        int skillVersionScore = skillVersion.score();
        int skillVersionPenalty = skillVersion.penalty();
        List<String> matchedDiagnostics = mergeDistinct(mergeDistinct(mergeDistinct(matched, backendEligibility.matchedDiagnostics()), skill.matchedDiagnostics()), skillVersion.matchedDiagnostics());
        List<String> missingDiagnostics = mergeDistinct(mergeDistinct(mergeDistinct(missing, backendEligibility.missingDiagnostics()), skill.missingDiagnostics()), skillVersion.missingDiagnostics());

        int score = Math.max(0, Math.min(100,
                capabilityScore + availabilityScore + slotScore + loadScore + siteScore + healthScore
                        + policyBonus + backendEligibilityScore + skillScore + skillVersionScore - penalty - backendEligibilityPenalty - skillPenalty - skillVersionPenalty));
        String reason = "capability=" + capabilityScore
                + ", availability=" + availabilityScore
                + ", slots=" + slotScore + "(available=" + agent.getAvailableSlots() + ")"
                + ", load=" + loadScore
                + "(current=" + agent.getCurrentTaskCount() + ",reserved=" + agent.getReservedTaskCount()
                + ",utilization=" + agent.getCapacityUtilization() + ")"
                + ", site=" + siteScore
                + ", health=" + healthScore
                + (backendEligibility.applied() ? ", backendEligibility=" + backendEligibilityScore + "(" + backendEligibility.reason() + ")" : "")
                + (skillVersion.applied() ? ", capabilityPolicyVersion=" + skillVersionScore + "(" + skillVersion.reason() + ")" : "")
                + (backendEligibilityPenalty > 0 ? ", backendEligibilityPenalty=" + backendEligibilityPenalty : "")
                + (skill.applied() ? ", capabilityContract=" + skillScore + "(" + skill.reason() + ")" : "")
                + (skillPenalty > 0 ? ", capabilityContractPenalty=" + skillPenalty : "")
                + (skillVersionPenalty > 0 ? ", capabilityPolicyVersionPenalty=" + skillVersionPenalty : "")
                + (penalty > 0 ? ", runtimePenalty=" + penalty + "(outboxPending=" + agent.getOutboxPending()
                + ",recoveryPending=" + agent.getRecoveryPendingAssignments() + ",draining=" + agent.isDraining() + ")" : "")
                + (policyBonus > 0 ? ", policyBonus=" + policyBonus : "")
                + (agent.getRuntimeFailureCount() > 0 ? ", runtimeFailureCount=" + agent.getRuntimeFailureCount() : "");
        boolean hasMissing = !missingDiagnostics.isEmpty() || backendEligibility.blockingFailure() || (skill.applied() && skill.blockingFailure()) || skillVersion.blockingFailure();
        int finalScore = backendEligibility.blockingFailure() ? 0 : (!runtimeAssignable || hasMissing) ? Math.min(score, 49) : score;
        Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        scoreBreakdown.put("capabilityScore", capabilityScore);
        scoreBreakdown.put("availabilityScore", availabilityScore);
        scoreBreakdown.put("slotScore", slotScore);
        scoreBreakdown.put("loadScore", loadScore);
        scoreBreakdown.put("siteScore", siteScore);
        scoreBreakdown.put("healthScore", healthScore);
        scoreBreakdown.put("policyBonus", policyBonus);
        scoreBreakdown.put("runtimePenalty", penalty);
        scoreBreakdown.put("runtimeAssignable", runtimeAssignable);
        scoreBreakdown.put("agentStatus", agent.getStatus() == null ? "UNKNOWN" : agent.getStatus().name());
        scoreBreakdown.put("runtimeCapacityAvailable", agent.getAvailableSlots() > 0 || agent.getEffectiveTaskCount() < Math.max(1, agent.getMaxConcurrentTasks()));
        scoreBreakdown.put("runtimeCapacityFull", runtimeCapacityFull);
        scoreBreakdown.put("backendEligibilityApplied", backendEligibility.applied());
        scoreBreakdown.put("backendEligibilityScore", backendEligibilityScore);
        scoreBreakdown.put("backendEligibilityPenalty", backendEligibilityPenalty);
        scoreBreakdown.put("backendEligibilityReason", backendEligibility.reason());
        scoreBreakdown.put("backendEligibilityRequiredProfiles", backendEligibility.requiredProfiles());
        scoreBreakdown.put("backendEligibilityApprovedProfiles", backendEligibility.approvedProfiles());
        scoreBreakdown.put("backendEligibilityBlocking", backendEligibility.blockingFailure());
        scoreBreakdown.put("skillApplied", skill.applied());
        scoreBreakdown.put("skillScore", skillScore);
        scoreBreakdown.put("skillPenalty", skillPenalty);
        scoreBreakdown.put("skillVersionApplied", skillVersion.applied());
        scoreBreakdown.put("skillVersionScore", skillVersionScore);
        scoreBreakdown.put("skillVersionPenalty", skillVersionPenalty);
        scoreBreakdown.put("rawScore", score);
        scoreBreakdown.put("finalScore", finalScore);
        scoreBreakdown.put("routingPolicy", policy.name());
        scoreBreakdown.put("routingPath", firstNonBlank(task == null ? null : task.getRoutingPath(), ""));
        scoreBreakdown.put("matchedFlowId", firstNonBlank(task == null ? null : task.getMatchedFlowId(), ""));
        scoreBreakdown.put("matchedRuleId", firstNonBlank(task == null ? null : task.getMatchedRuleId(), ""));
        scoreBreakdown.put("requestedSkill", firstNonBlank(task == null ? null : task.getRequestedSkill(), ""));
        scoreBreakdown.put("matchedCapabilities", matchedDiagnostics);
        scoreBreakdown.put("missingCapabilities", missingDiagnostics);
        scoreBreakdown.put("effectiveCapabilities", effectiveCapabilities);
        scoreBreakdown.put("skillReason", skill.reason());
        scoreBreakdown.put("skillVersionReason", skillVersion.reason());
        scoreBreakdown.put("blockingFailure", backendEligibility.blockingFailure() || skill.blockingFailure() || skillVersion.blockingFailure());
        scoreBreakdown.put("runtime", Map.of(
                "availableSlots", agent.getAvailableSlots(),
                "currentTaskCount", agent.getCurrentTaskCount(),
                "reservedTaskCount", agent.getReservedTaskCount(),
                "capacityUtilization", agent.getCapacityUtilization(),
                "outboxPending", agent.getOutboxPending(),
                "recoveryPendingAssignments", agent.getRecoveryPendingAssignments(),
                "runtimeFailureCount", agent.getRuntimeFailureCount(),
                "draining", agent.isDraining()
        ));
        return new AgentCandidateScore(
                agent.getAgentId(), agent.getOwnerGatewayNodeId(), agent.getAgentSessionId(), agent.getSiteId(),
                agent.getStatus() == null ? null : agent.getStatus().name(),
                finalScore, matchedDiagnostics, missingDiagnostics, reason, immutableNullableMap(scoreBreakdown));
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

    private String v2DecisionSuffix(V2RoutingComparison comparison, EligibilityEngineMode mode, String selectedAgentId) {
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

    private DispatchUserFacingError userFacingV2EnforcementError(TaskRecord task, V2RoutingComparison comparison) {
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

    private boolean hasRequiredV2ScoreBreakdown(AgentCandidateScore score) {
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

    private DispatchUserFacingError userFacingV2ScoreBreakdownRequiredError(TaskRecord task, List<String> missingAgentIds) {
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


    private BackendEligibilityScore evaluateBackendEligibility(TaskRecord task, AgentSnapshot agent) {
        return BackendEligibilityScore.notApplied();
    }


    private TaskDispatchRequirements resolveTaskDispatchRequirements(TaskRecord task) {
        return null;
    }

    private boolean isProfileNotConfigured(TaskDispatchRequirements requirements) {
        if (requirements == null) {
            return false;
        }
        String source = normalize(requirements.getRequirementSource());
        return requirements.getRequiredProfiles().isEmpty()
                && ("NO_ASSIGNMENT_PROFILE_MATCH".equals(source)
                || "NO_SOURCE_SYSTEM_PROFILE_MATCH".equals(source)
                || "NO_TASK_DEFINITION_CONTRACT".equals(source)
                || "NO_ACTIVE_TASK_DEFINITION_PROFILE_MATCH".equals(source));
    }

    private DispatchUserFacingError userFacingProfileNotConfiguredError(TaskRecord task, TaskDispatchRequirements requirements) {
        boolean taskDefinitionMissing = requirements != null && "NO_TASK_DEFINITION_CONTRACT".equals(normalize(requirements.getRequirementSource()));
        String resolvedTaskType = firstNonBlank(requirements == null ? null : requirements.getTaskType(), taskTypeCode(task));
        String resolvedSourceSystem = firstNonBlank(requirements == null ? null : requirements.getSourceSystem(), task == null ? null : task.getSourceSystem());
        return DispatchUserFacingError.of(
                taskDefinitionMissing ? DispatchUserFacingErrorCode.DISPATCH_TASK_DEFINITION_NOT_FOUND : DispatchUserFacingErrorCode.DISPATCH_PROFILE_NOT_CONFIGURED,
                "HIGH",
                taskDefinitionMissing ? "此任務沒有 ACTIVE Dispatch Task Definition 契約。" : "此任務找不到 ACTIVE 的嚴格派工 Profile / Dispatch Flow coverage 契約。",
                taskDefinitionMissing
                        ? "請到 Dispatch Task Definitions，依 sourceSystem=" + display(resolvedSourceSystem)
                        + "、taskType=" + display(resolvedTaskType) + " 建立並啟用 Task Definition，再建立對應的一任務一 Dispatch Flow coverage。"
                        : "請確認 sourceSystem=" + display(resolvedSourceSystem)
                        + "、taskType=" + display(resolvedTaskType) + " 已有 ACTIVE Dispatch Task Definition，且已有 ACTIVE 的一任務一 Dispatch Flow coverage / Dispatch Flow Agent Assignment；Agent 只核准 capability 或 legacy 多任務 Scope 仍不足以派工。",
                taskDefinitionMissing ? "runbooks/dispatch/task-definition-contract" : "runbooks/dispatch/profile-not-configured",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", resolvedTaskType,
                        "rawTaskType", taskTypeCode(task),
                        "sourceSystem", resolvedSourceSystem),
                details(
                        "requirementSource", requirements == null ? null : requirements.getRequirementSource(),
                        "requiredProfiles", requirements == null ? List.of() : requirements.getRequiredProfiles(),
                        "taskDefinitionIds", requirements == null ? List.of() : requirements.getTaskDefinitionIds(),
                        "requiredCapabilities", requirements == null ? List.of() : requirements.getRequiredCapabilities(),
                        "requiredRuntimeFeatures", requirements == null ? List.of() : requirements.getRequiredRuntimeFeatures()));
    }

    private DispatchUserFacingError userFacingNoCandidateError(TaskRecord task, CandidateFilterResult candidatePool) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_NO_AGENT_ONLINE,
                "HIGH",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? poolFirstNoCandidateMessage(candidatePool) : "目前沒有可評分的 Agent 可派工。",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? poolFirstNoCandidateAction(candidatePool) : "請先確認至少一個 Agent 已連線、Credential 為 ACTIVE，且沒有被停用或排除。",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? "runbooks/dispatch/pool-first-troubleshooting" : "runbooks/dispatch/no-agent-online",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", taskTypeCode(task),
                        "sourceSystem", task == null ? null : task.getSourceSystem(),
                        "targetPoolId", candidatePool == null ? null : candidatePool.targetPoolId(),
                        "targetPoolCode", candidatePool == null ? null : candidatePool.targetPoolCode(),
                        "poolMemberCount", candidatePool == null ? 0 : candidatePool.memberCount(),
                        "eligibleAgentCount", candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                        "blockerCode", candidatePool == null ? null : candidatePool.poolBlockerCode(),
                        "poolBlocker", candidatePool == null ? null : candidatePool.poolBlockerCode()),
                details(
                        "includedCandidates", candidatePool == null ? 0 : candidatePool.included().size(),
                        "reservationExcluded", candidatePool == null ? Set.of() : candidatePool.reservationExcluded(),
                        "poisonExcluded", candidatePool == null ? Set.of() : candidatePool.poisonExcluded(),
                        "targetPoolId", candidatePool == null ? null : candidatePool.targetPoolId(),
                        "targetPoolCode", candidatePool == null ? null : candidatePool.targetPoolCode(),
                        "poolMemberCount", candidatePool == null ? 0 : candidatePool.memberCount(),
                        "eligibleAgentCount", candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                        "blockerCode", candidatePool == null ? null : candidatePool.poolBlockerCode(),
                        "poolBlocker", candidatePool == null ? null : candidatePool.poolBlockerCode()));
    }

    private String poolFirstNoCandidateMessage(CandidateFilterResult candidatePool) {
        String blocker = normalize(candidatePool == null ? null : candidatePool.poolBlockerCode());
        if ("POOL_HAS_NO_ACTIVE_MEMBER".equals(blocker)) return "目標 Agent Pool 沒有啟用中的成員。";
        if ("POOL_AGENT_RUNTIME_NOT_FOUND".equals(blocker)) return "目標 Agent Pool 的成員尚未建立 runtime binding。";
        if ("POOL_AGENT_OFFLINE".equals(blocker)) return "目標 Agent Pool 的成員目前離線或心跳不可用。";
        if ("POOL_AGENT_CAPACITY_FULL".equals(blocker)) return "目標 Agent Pool 的成員容量已滿。";
        if ("POOL_AGENT_BACKOFF".equals(blocker)) return "目標 Agent Pool 的成員暫時被 backoff 排除。";
        if ("RULE_TARGET_POOL_NOT_FOUND".equals(blocker)) return "Flow Rule 指定的 target Pool 不存在或未啟用。";
        return "目標 Agent Pool 目前沒有可派工的 Agent。";
    }

    private String poolFirstNoCandidateAction(CandidateFilterResult candidatePool) {
        String blocker = normalize(candidatePool == null ? null : candidatePool.poolBlockerCode());
        if ("POOL_HAS_NO_ACTIVE_MEMBER".equals(blocker)) return "到 Agent Pool 管理頁加入至少一個已核准 Agent，或啟用既有 Pool member。";
        if ("POOL_AGENT_RUNTIME_NOT_FOUND".equals(blocker)) return "先完成 Agent setup/runtime binding，再把該 Agent 加入此 Pool。";
        if ("POOL_AGENT_OFFLINE".equals(blocker)) return "啟動 Pool 內 Agent runtime，確認 credential active、heartbeat healthy。";
        if ("POOL_AGENT_CAPACITY_FULL".equals(blocker)) return "等待 Pool 內 Agent 釋放容量、提高 capacity，或加入新的 Agent。";
        if ("POOL_AGENT_BACKOFF".equals(blocker)) return "檢查 Agent 近期失敗紀錄，清除 backoff 或改派其他 Pool member。";
        if ("RULE_TARGET_POOL_NOT_FOUND".equals(blocker)) return "修正 Flow Rule 的 target Pool，或改用 Source Flow default Pool。";
        return "請確認目標 Pool 內已有啟用成員，且成員 Agent 已核准、已連線並有可用容量。";
    }

    private DispatchUserFacingError userFacingBelowMinimumError(TaskRecord task, AgentCandidateScore selected, CandidateFilterResult candidatePool) {
        Map<String, Object> breakdown = selected.scoreBreakdown();
        boolean backendBlocking = booleanBreakdown(breakdown, "backendEligibilityBlocking");
        List<String> requiredProfiles = stringListBreakdown(breakdown, "backendEligibilityRequiredProfiles");
        List<String> approvedProfiles = stringListBreakdown(breakdown, "backendEligibilityApprovedProfiles");
        if (backendBlocking && requiredProfiles.isEmpty()) {
            return userFacingProfileNotConfiguredError(task, null);
        }
        if (backendBlocking) {
            String backendCode = firstBackendDispatchCode(selected);
            if (!blank(backendCode)) {
                return userFacingBackendEligibilityError(task, selected, backendCode, requiredProfiles, approvedProfiles, candidatePool);
            }
            if (containsAllNormalized(approvedProfiles, requiredProfiles)) {
                return DispatchUserFacingError.of(
                        DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE,
                        "HIGH",
                        "候選 Agent 已具備必要 Profile，但目前 runtime 狀態不可接任務。",
                        "請確認 Agent runtime 已在線、未 draining/backoff，且仍有可用容量；修正後請從 Recovery Console 立即重試。",
                        "runbooks/dispatch/agent-not-assignable",
                        details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId(), "requiredProfiles", requiredProfiles, "approvedProfiles", approvedProfiles),
                        technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
            }
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_PROFILE_MISSING,
                    "HIGH",
                    "目前沒有 Agent 取得此任務所需的後台派工資格。",
                    "請將 Profile " + requiredProfiles + " 指派並核准給可用 Agent；若該 Profile 需要認證，請先執行 Certification。",
                    "runbooks/dispatch/agent-profile-missing",
                    details("taskId", task == null ? null : task.getTaskId(), "requiredProfiles", requiredProfiles),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
        }
        if (booleanBreakdown(breakdown, "runtimeCapacityFull")) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY,
                    "MEDIUM",
                    "候選 Agent 目前沒有足夠容量接新任務。",
                    "請等待目前任務完成、增加 Agent maxConcurrentTasks，或啟動更多 Agent。",
                    "runbooks/dispatch/agent-no-capacity",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
        }
        boolean runtimeAssignable = booleanBreakdown(breakdown, "runtimeAssignable");
        Number availabilityScore = numericBreakdown(breakdown, "availabilityScore");
        if (!runtimeAssignable || (availabilityScore != null && availabilityScore.intValue() <= 0)) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE,
                    "HIGH",
                    "候選 Agent 目前不可接任務。",
                    "請確認 Agent 已啟用、未被治理規則封鎖，未處於 draining/backoff/offline，且 runtime 狀態允許派工。",
                    "runbooks/dispatch/agent-not-assignable",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
        }
        Number slotScore = numericBreakdown(breakdown, "slotScore");
        Number loadScore = numericBreakdown(breakdown, "loadScore");
        if ((slotScore != null && slotScore.intValue() <= 0) || (loadScore != null && loadScore.intValue() <= 0)) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY,
                    "MEDIUM",
                    "候選 Agent 目前沒有足夠容量接新任務。",
                    "請等待目前任務完成、增加 Agent maxConcurrentTasks，或啟動更多 Agent。",
                    "runbooks/dispatch/agent-no-capacity",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
        }
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD,
                "MEDIUM",
                "目前最佳 Agent 的派工分數低於系統門檻。",
                "請檢查候選 Agent 的 runtime capacity、site policy 與健康狀態。",
                "runbooks/dispatch/score-below-threshold",
                details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
    }

    private DispatchUserFacingError userFacingBackendEligibilityError(TaskRecord task, AgentCandidateScore selected, String backendCode, List<String> requiredProfiles, List<String> approvedProfiles, CandidateFilterResult candidatePool) {
        DispatchUserFacingErrorCode code = toUserFacingCode(backendCode);
        String message = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "候選 Agent 的後台 Profile 已命中，但 runtime 線上狀態未通過。";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "此任務沒有 ACTIVE Dispatch Task Definition 契約，Routing 不允許使用 legacy source/task fallback。";
            case DISPATCH_PROFILE_POLICY_MISSING -> "此 Dispatch Flow Agent Assignment 缺少 ACTIVE Policy Binding，不能以未治理 Profile 派工。";
            case DISPATCH_PROFILE_CAPABILITY_MISSING -> "此 Dispatch Flow Agent Assignment 缺少 ACTIVE Capability Binding，或 Agent 未具備所需能力。";
            case DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL -> "候選 Agent 的必要 Capability 仍在等待核准。";
            case DISPATCH_AGENT_CAPABILITY_REVOKED -> "候選 Agent 的必要 Capability 已被撤銷、暫停、拒絕或過期。";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "候選 Agent 已具備必要 Profile，但目前 runtime 狀態不可接任務。";
            case DISPATCH_AGENT_NO_CAPACITY -> "候選 Agent 已具備必要 Profile，但目前沒有足夠容量接新任務。";
            case DISPATCH_RUNTIME_FEATURE_MISSING -> "候選 Agent 缺少必要 Runtime Feature 的治理紀錄。";
            case DISPATCH_RUNTIME_FEATURE_UNTRUSTED -> "候選 Agent 只回報了 Runtime Feature observation，但尚未被信任。";
            case DISPATCH_RUNTIME_FEATURE_REVOKED -> "候選 Agent 的必要 Runtime Feature trust 已被撤銷或暫停。";
            default -> "候選 Agent 未通過後台治理型 Routing Eligibility Contract。";
        };
        String nextAction = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "請啟動或重新連線 Agent runtime，確認 heartbeat 更新後再由 Recovery Console 立即重試。";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "請到 Dispatch Task Definitions 建立或啟用對應 sourceSystem/taskType，並確認 Profile 參照該契約。";
            case DISPATCH_PROFILE_POLICY_MISSING -> "請到 Dispatch Flow Agent Assignments → Policy Bindings 綁定同 Task Definition 下的 ACTIVE policy。";
            case DISPATCH_PROFILE_CAPABILITY_MISSING -> "請到 Dispatch Flow Agent Assignments → Required Capabilities 綁定 ACTIVE Capability，並到 Agent Detail 核准 Agent Capability。";
            case DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL -> "請到 Agent Detail → Capabilities 審核並 Approve 必要 Capability assignment。";
            case DISPATCH_AGENT_CAPABILITY_REVOKED -> "請重新 request/approve Capability，或改派具備 APPROVED Capability 的 Agent。";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "請確認 Agent 已啟用、runtime 未 draining/backoff/offline，且狀態已正規化為 IDLE 或 BUSY_ACCEPTING。";
            case DISPATCH_AGENT_NO_CAPACITY -> "請等待目前任務完成、提高 maxConcurrentTasks，或啟動更多具備相同 Profile 的 Agent。";
            case DISPATCH_RUNTIME_FEATURE_MISSING, DISPATCH_RUNTIME_FEATURE_UNTRUSTED -> "請到 Agent Detail → Runtime Features 建立 observation 後 Verify/Trust 必要 runtime feature。";
            case DISPATCH_RUNTIME_FEATURE_REVOKED -> "請重新驗證 runtime feature，或改派具備 TRUSTED feature 的 Agent。";
            default -> "請依 Troubleshooting Wizard 第一個 FAILED step 修正治理契約。";
        };
        String runbook = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "runbooks/dispatch/no-agent-online";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "runbooks/dispatch/task-definition-contract";
            case DISPATCH_PROFILE_POLICY_MISSING -> "runbooks/dispatch/profile-policy-binding";
            case DISPATCH_PROFILE_CAPABILITY_MISSING, DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL, DISPATCH_AGENT_CAPABILITY_REVOKED -> "runbooks/dispatch/capability-contract";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "runbooks/dispatch/agent-not-assignable";
            case DISPATCH_AGENT_NO_CAPACITY -> "runbooks/dispatch/agent-no-capacity";
            case DISPATCH_RUNTIME_FEATURE_MISSING, DISPATCH_RUNTIME_FEATURE_UNTRUSTED, DISPATCH_RUNTIME_FEATURE_REVOKED -> "runbooks/dispatch/runtime-feature-trust";
            default -> "runbooks/dispatch/routing-eligibility-contract";
        };
        return DispatchUserFacingError.of(
                code,
                "HIGH",
                message,
                nextAction,
                runbook,
                details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected == null ? null : selected.agentId(), "requiredProfiles", requiredProfiles),
                technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool));
    }

    private DispatchUserFacingErrorCode toUserFacingCode(String value) {
        if (blank(value)) {
            return DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD;
        }
        switch (value) {
            case "RUNTIME_ONLINE":
                return DispatchUserFacingErrorCode.DISPATCH_NO_AGENT_ONLINE;
            case "CAPACITY_AVAILABLE":
                return DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY;
            case "NOT_DRAINING", "RUNTIME_BINDING_ACTIVE", "CREDENTIAL_VALID", "AGENT_APPROVED", "AGENT_RISK_STATUS", "EVALUATION_ERROR":
                return DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE;
            default:
                break;
        }
        try {
            return DispatchUserFacingErrorCode.valueOf(value);
        } catch (Exception ex) {
            return DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD;
        }
    }

    private String firstBackendDispatchCode(AgentCandidateScore selected) {
        if (selected == null) return null;
        List<String> diagnostics = new ArrayList<>();
        if (selected.missingCapabilities() != null) diagnostics.addAll(selected.missingCapabilities());
        diagnostics.addAll(stringListBreakdown(selected.scoreBreakdown(), "missingCapabilities"));
        for (String item : diagnostics) {
            String code = extractDispatchCode(item);
            if (!blank(code)) return code;
            code = extractBackendEligibilityCode(item);
            if (!blank(code)) return code;
        }
        return null;
    }

    private String extractBackendEligibilityCode(String value) {
        if (blank(value)) return null;
        String marker = "backendEligibility:";
        int start = value.indexOf(marker);
        if (start < 0) return null;
        int codeStart = start + marker.length();
        int codeEnd = value.indexOf(':', codeStart);
        if (codeEnd < 0 || codeEnd <= codeStart) return null;
        return value.substring(codeStart, codeEnd);
    }

    private String extractDispatchCode(String value) {
        if (blank(value)) return null;
        int start = value.indexOf("DISPATCH_");
        if (start < 0) return null;
        int end = start;
        while (end < value.length()) {
            char ch = value.charAt(end);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                end++;
            } else {
                break;
            }
        }
        return value.substring(start, end);
    }

    private void applyUserFacingError(RoutingDecisionRecord decision, DispatchUserFacingError error) {
        decision.setUserFacingError(error);
        decision.setDecisionReason(error == null ? null : error.toLegacyDecisionReason());
    }

    private boolean containsAllNormalized(List<String> approvedProfiles, List<String> requiredProfiles) {
        if (requiredProfiles == null || requiredProfiles.isEmpty()) {
            return false;
        }
        Set<String> approved = approvedProfiles == null ? Set.of() : approvedProfiles.stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .collect(java.util.stream.Collectors.toSet());
        return requiredProfiles.stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .allMatch(approved::contains);
    }

    private Map<String, Object> technicalBelowMinimum(AgentCandidateScore selected, List<String> requiredProfiles, List<String> approvedProfiles, CandidateFilterResult candidatePool) {
        return details(
                "selectedAgent", selected.agentId(),
                "selectedScore", selected.score(),
                "minimumScore", properties.getMinimumScore(),
                "requiredProfiles", requiredProfiles,
                "approvedProfiles", approvedProfiles,
                "scoring", selected.reason(),
                "scoreBreakdown", selected.scoreBreakdown(),
                "reservationExcluded", candidatePool == null ? Set.of() : candidatePool.reservationExcluded(),
                "poisonExcluded", candidatePool == null ? Set.of() : candidatePool.poisonExcluded());
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

    private Number numericBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        return value instanceof Number number ? number : null;
    }

    private boolean booleanBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        return value instanceof Boolean bool && bool;
    }

    private List<String> stringListBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private String taskTypeCode(TaskRecord task) {
        return task == null ? null : normalize(task.getEffectiveTaskTypeCode());
    }

    private String display(String value) {
        return blank(value) ? "UNKNOWN" : value;
    }

    private boolean isCertificationTargetTask(TaskRecord task) {
        return task != null && ("CERTIFICATION_TARGET_AGENT".equalsIgnoreCase(task.getRoutingPolicy())
                || "AGENT_CERTIFICATION".equalsIgnoreCase(task.getObjectType())
                || "CERTIFICATION_RUN".equalsIgnoreCase(task.getEventType()));
    }

    private int policyBonus(RoutingPolicy policy, List<String> missing, int siteScore) {
        boolean completeCapability = missing == null || missing.isEmpty();
        return switch (policy) {
            case FLOW_RULE -> completeCapability ? 20 : 0;
            case CAPABILITY_FIRST -> completeCapability ? 10 : 0;
            case LOCAL_FIRST -> siteScore > 0 ? 15 : 0;
            case LOAD_BALANCED -> 5;
            default -> 0;
        };
    }

    private SkillScore evaluateSkillAware(TaskRecord task, AgentSnapshot agent) {
        if (!properties.isSkillAwareEnabled() || skillRegistryService == null) {
            return SkillScore.notApplied();
        }
        AgentSkillEvaluationRequest request = skillRequestFor(task);
        if (!requiresSkillEvaluation(request)) {
            return SkillScore.notApplied();
        }
        if (dispatchSkillEvaluationService == null) {
            return SkillScore.notApplied();
        }
        AgentSkillEvaluationResult result = dispatchSkillEvaluationService.evaluate(agent, request);
        List<String> matched = result.getMatchedSkillCodes().stream().map(value -> "skill:" + value).toList();
        List<String> missing = result.getMissingRequirements().stream().map(value -> "skill:" + value).toList();
        if (result.isEligible()) {
            int bonus = 20 + Math.min(10, result.getMatchedSkillCodes().size() * 5);
            return new SkillScore(true, Math.min(30, bonus), 0, matched, List.of(),
                    "eligible matched=" + result.getMatchedSkillCodes(), false);
        }
        int penalty = properties.isSkillAwareEnforced() ? 40 : 0;
        return new SkillScore(true, 0, penalty, matched, missing,
                "ineligible missing=" + result.getMissingRequirements(), properties.isSkillAwareEnforced());
    }

    private SkillVersionScore evaluateSkillVersionCompatibility(TaskRecord task, AgentSnapshot agent) {
        if (!properties.isSkillVersionCompatibilityEnabled()) {
            return SkillVersionScore.notApplied();
        }
        Map<String, Integer> required = requiredSkillVersions(task);
        if (required.isEmpty()) {
            return SkillVersionScore.notApplied();
        }
        Map<String, Integer> agentVersions = agentSkillVersions(agent);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int actual = agentVersions.getOrDefault(entry.getKey(), 0);
            if (actual >= entry.getValue()) {
                matched.add("skillVersion:" + entry.getKey() + ">=" + entry.getValue() + " actual=" + actual);
            } else {
                missing.add("skillVersion:" + entry.getKey() + ">=" + entry.getValue() + " actual=" + actual);
            }
        }
        if (missing.isEmpty()) {
            return new SkillVersionScore(true, Math.min(10, matched.size() * 5), 0, matched, List.of(),
                    "compatible " + matched, false);
        }
        int penalty = properties.isSkillVersionEnforced() ? 40 : 0;
        return new SkillVersionScore(true, 0, penalty, matched, missing,
                "incompatible " + missing, properties.isSkillVersionEnforced());
    }

    private AgentSkillEvaluationRequest skillRequestFor(TaskRecord task) {
        List<String> requiredCapabilities = routingRequiredCapabilities(task);
        AgentSkillEvaluationRequest request = new AgentSkillEvaluationRequest();
        request.setRequiredCapabilities(requiredCapabilities);
        request.setSiteCode(task.getSiteId());
        KnownSkillMatch known = findKnownSkill(requiredCapabilities, task);
        if (known != null) {
            request.setDomain(known.domain());
            request.setTaskType(known.taskType());
            request.setProvider(known.provider());
            request.setRequiredToolPolicy(known.toolPolicy());
            request.setOperation(known.operation());
        }
        for (String required : requiredCapabilities) {
            if (isOperation(required)) request.setOperation(required);
            if (isToolPolicy(required)) request.setRequiredToolPolicy(required);
            if (required.startsWith("DATA_CLASS:")) request.getDataClasses().add(required.substring("DATA_CLASS:".length()));
            if (required.startsWith("DATA:")) request.getDataClasses().add(required.substring("DATA:".length()));
            if (required.startsWith("PROVIDER:")) request.setProvider(required.substring("PROVIDER:".length()));
            if (required.startsWith("DOMAIN:")) request.setDomain(required.substring("DOMAIN:".length()));
        }
        return request;
    }

    private KnownSkillMatch findKnownSkill(List<String> requiredCapabilities, TaskRecord task) {
        if (skillRegistryService == null) return null;
        List<AgentSkillDefinition> skills = skillRegistryService.search(null, true);
        for (AgentSkillDefinition skill : skills) {
            String skillCode = normalize(skill.getSkillCode());
            if (requiredCapabilities.contains(skillCode) || intersectsNormalized(skill.getTaskTypes(), requiredCapabilities)) {
                return new KnownSkillMatch(
                        skill.getDomain(),
                        firstOrDefault(intersection(skill.getTaskTypes(), requiredCapabilities), skillCode),
                        firstOrDefault(intersection(skill.getProviders(), requiredCapabilities), first(skill.getProviders())),
                        firstOrDefault(intersection(skill.getToolPolicies(), requiredCapabilities), first(skill.getToolPolicies())),
                        firstOrDefault(intersection(skill.getOperations(), requiredCapabilities), null));
            }
        }
        String taskTypeFromTask = taskTypeCode(task);
        if (!blank(taskTypeFromTask)) {
            String taskType = normalize(taskTypeFromTask);
            for (AgentSkillDefinition skill : skills) {
                if (containsNormalized(skill.getTaskTypes(), taskType)) {
                    return new KnownSkillMatch(skill.getDomain(), taskType, first(skill.getProviders()), first(skill.getToolPolicies()), first(skill.getOperations()));
                }
            }
        }
        return null;
    }

    private boolean requiresSkillEvaluation(AgentSkillEvaluationRequest request) {
        return request != null
                && (!blank(request.getDomain())
                || !blank(request.getTaskType())
                || !blank(request.getProvider())
                || !blank(request.getOperation())
                || !blank(request.getRequiredToolPolicy())
                || !request.getDataClasses().isEmpty());
    }

    private List<String> effectiveCapabilities(AgentSnapshot agent) {
        if (dispatchSkillEvaluationService != null) {
            return dispatchSkillEvaluationService.effectiveDispatchCapabilities(agent);
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (agent.getCapabilities() != null) {
            agent.getCapabilities().stream().map(this::normalize).filter(value -> !blank(value)).forEach(capabilities::add);
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        if (profile != null && !profile.isEmpty()) {
            addCapabilityValues(capabilities, profile.get("supportedTaskTypes"));
            addCapabilityValues(capabilities, profile.get("supportedIssueProviders"));
            addCapabilityValues(capabilities, profile.get("toolPolicies"));
            addCapabilityValues(capabilities, profile.get("domains"));
            addCapabilityValues(capabilities, profile.get("domain"));
            addCapabilityValues(capabilities, profile.get("systems"));
            addCapabilityValues(capabilities, profile.get("system"));
            addCapabilityValues(capabilities, profile.get("skills"));
            Object executorMode = profile.get("executorMode");
            if (executorMode != null && !executorMode.toString().isBlank()) {
                capabilities.add(normalize(executorMode.toString()));
            }
        }
        return capabilities.stream().toList();
    }

    private void addCapabilityValues(Set<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCapabilityValue(target, item);
            }
            return;
        }
        addCapabilityValue(target, value);
    }

    @SuppressWarnings("unchecked")
    private void addCapabilityValue(Set<String> target, Object item) {
        if (item == null) return;
        if (item instanceof Map<?, ?> map) {
            addCapabilityValue(target, map.get("skillCode"));
            addCapabilityValue(target, map.get("domain"));
            addCapabilityValues(target, map.get("domains"));
            addCapabilityValues(target, map.get("taskTypes"));
            addCapabilityValues(target, map.get("providers"));
            addCapabilityValues(target, map.get("operations"));
            addCapabilityValues(target, map.get("toolPolicies"));
            addCapabilityValues(target, map.get("dataClasses"));
            return;
        }
        String normalized = normalize(item.toString());
        if (!blank(normalized)) {
            target.add(normalized);
        }
    }

    private int runtimeLoadScore(AgentSnapshot agent, RoutingPolicy policy) {
        if (!properties.isLoadAwareScoringEnabled() || agent.isDraining()) {
            return 0;
        }
        double utilization = Math.max(0.0d, Math.min(1.0d, agent.getCapacityUtilization()));
        int maxScore = policy == RoutingPolicy.LOAD_BALANCED ? 20 : 15;
        int utilizationScore = (int) Math.round(maxScore * (1.0d - utilization));
        int effectiveTaskPenalty = Math.max(0, agent.getEffectiveTaskCount() * 2);
        return Math.max(0, utilizationScore - effectiveTaskPenalty);
    }

    private boolean isCapacityFull(AgentSnapshot agent) {
        if (agent == null) {
            return false;
        }
        return agent.getAvailableSlots() <= 0
                && agent.getEffectiveTaskCount() >= Math.max(1, agent.getMaxConcurrentTasks());
    }

    private int runtimePenalty(AgentSnapshot agent) {
        int penalty = 0;
        if (agent.isDraining()) {
            penalty += 100;
        }
        penalty += Math.min(20, Math.max(0, agent.getOutboxPending()) * 2);
        penalty += Math.min(20, Math.max(0, agent.getRecoveryPendingAssignments()) * 5);
        if (properties.isRuntimeFailurePenaltyEnabled()) {
            penalty += Math.min(20, Math.max(0, agent.getRuntimeFailureCount()) * 3);
        }
        return penalty;
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
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, blockingReasonCode(decision));
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

    private String blockingReasonCode(RoutingDecisionRecord decision) {
        if (decision.getStatus() == RoutingDecisionStatus.SELECTED) return "none";
        if (decision.getUserFacingError() != null && decision.getUserFacingError().getCode() != null) {
            return normalizeObservationValue(decision.getUserFacingError().getCode().name());
        }
        return switch (decision.getStatus()) {
            case SUPPRESSED -> "assignment_disabled";
            case MANUAL_REVIEW_REQUIRED -> "manual_review_required";
            case NO_CANDIDATE -> "no_candidate";
            case SELECTED -> "none";
        };
    }

    private String candidateBlockingReason(V2RoutingComparison comparison, EligibilityEngineMode mode, CandidateFilterResult pool) {
        if (mode != null && mode.enforce() && comparison != null && comparison.hasGlobalBlockingReasons()) {
            List<String> codes = comparison.globalReasonCodes();
            if (!codes.isEmpty()) return normalizeObservationValue(codes.getFirst());
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

    private String flowRuleDecisionSuffix(TaskRecord task) {
        if (!isFlowRuleTask(task)) {
            return "";
        }
        return "; flowRule=FLOW_RULE matchedFlowId=" + task.getMatchedFlowId()
                + ", matchedRuleId=" + task.getMatchedRuleId()
                + ", eventStage=" + display(task.getEventStage())
                + ", targetPoolId=" + display(task.getTargetPoolId())
                + ", assignedPoolId=" + display(task.getAssignedPoolId())
                + ", classificationStatus=" + display(task.getClassificationStatus())
                + ", capabilityTagReference=" + display(task.getRequestedSkill());
    }

    private RoutingDecisionRecord saveAndRecord(RoutingDecisionRecord decision) {
        RoutingDecisionRecord saved = routingDecisionRepository.save(decision);
        log.debug("routing_decision_saved decisionId={} taskId={} status={} selectedAgentId={} policy={} reason={}",
                saved.getDecisionId(), saved.getTaskId(), saved.getStatus(), saved.getSelectedAgentId(), saved.getRoutingPolicy(), saved.getDecisionReason());
        if (metrics != null) {
            metrics.recordRoutingDecision(saved);
        }
        return saved;
    }

    private RoutingPolicy resolvePolicy(TaskRecord task) {
        if (isFlowRuleTask(task)) {
            return RoutingPolicy.FLOW_RULE;
        }
        // Stage 7: work without a persisted Flow/Rule match is held for
        // operator correction. Runtime never reconstructs authority from
        // historical direct Flow Agent Assignment, legacy coverage, Capability tag metadata, or
        // direct runtime evidence.
        return RoutingPolicy.MANUAL_REVIEW;
    }

    private boolean requiresManualReview(RoutingPolicy policy) {
        return policy == RoutingPolicy.MANUAL_REVIEW;
    }

    private boolean requiresLocalSearch(RoutingPolicy policy) {
        return policy == RoutingPolicy.LOCAL_ONLY || policy == RoutingPolicy.LOCAL_FIRST;
    }

    private boolean allowsGlobalFallback(RoutingPolicy policy) {
        return policy == RoutingPolicy.LOCAL_FIRST;
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

    private boolean isFlowRuleTask(TaskRecord task) {
        if (!properties.isFlowRuleRoutingEnabled() || task == null || blank(task.getMatchedFlowId())) {
            return false;
        }
        String path = normalize(task.getRoutingPath());
        boolean standardPath = "FLOW_RULE".equals(path) || "SOURCE_FLOW_DEFAULT_POOL".equals(path) || "SOURCE_FLOW_POOL".equals(path);
        return standardPath && (!blank(task.getMatchedRuleId()) || !blank(task.getTargetPoolId()) || !blank(task.getAssignedPoolId()));
    }

    private List<String> flowRuleRequiredSkills(TaskRecord task) {
        // R12.10: do not merge legacy task required_capabilities_json into Flow Rule
        // routing.  Those values are often fallback capabilities such as
        // INCIDENT_ANALYSIS and would incorrectly require Agents to satisfy the old
        // Task Definition/Profile contract after a Flow-owned rule has matched.
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        String requestedSkill = normalize(task == null ? null : task.getRequestedSkill());
        if (!blank(requestedSkill)) {
            skills.add(requestedSkill);
        }
        return skills.stream().toList();
    }

    private List<String> normalizedRequiredCapabilities(TaskRecord task) {
        return task == null || task.getRequiredCapabilities() == null ? List.of() : task.getRequiredCapabilities().stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private List<String> routingRequiredCapabilities(TaskRecord task) {
        if (isFlowRuleTask(task)) {
            // Phase 32-D: Capability is Agent metadata only. Source Flow / Pool-first routing
            // must not turn requestedSkill or required_capabilities_json into a blocking gate.
            return List.of();
        }
        return normalizedRequiredCapabilities(task).stream()
                .filter(value -> !isSkillVersionHint(value))
                .toList();
    }

    private boolean isSkillVersionHint(String value) {
        return !blank(value) && (value.startsWith("SKILL_VERSION:") || value.contains("@"));
    }


    private Map<String, Integer> requiredSkillVersions(TaskRecord task) {
        LinkedHashMap<String, Integer> required = new LinkedHashMap<>();
        for (String capability : normalizedRequiredCapabilities(task)) {
            addRequiredSkillVersion(required, capability);
        }
        return required;
    }

    private void addRequiredSkillVersion(Map<String, Integer> required, String value) {
        if (blank(value)) return;
        String normalized = normalize(value);
        if (normalized.startsWith("SKILL_VERSION:")) {
            String rest = normalized.substring("SKILL_VERSION:".length());
            int split = rest.lastIndexOf(':');
            if (split > 0) {
                putVersion(required, rest.substring(0, split), intValue(rest.substring(split + 1)));
                return;
            }
        }
        int at = normalized.lastIndexOf('@');
        if (at > 0) {
            putVersion(required, normalized.substring(0, at), intValue(normalized.substring(at + 1).replace("V", "")));
        }
    }

    private Map<String, Integer> agentSkillVersions(AgentSnapshot agent) {
        LinkedHashMap<String, Integer> versions = new LinkedHashMap<>();
        if (agent == null || agent.getCapabilityProfile() == null) {
            return versions;
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        addSkillVersions(versions, profile.get("skillVersions"));
        addSkillVersions(versions, profile.get("skills"));
        return versions;
    }

    private void addSkillVersions(Map<String, Integer> versions, Object raw) {
        if (raw == null) return;
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) addSkillVersions(versions, item);
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            String skillCode = firstNonBlank(map.get("skillCode"), map.get("code"), map.get("name"));
            Integer version = firstInt(map.get("version"), map.get("publishedVersion"), map.get("policyVersion"));
            putVersion(versions, skillCode, version);
            return;
        }
        addRequiredSkillVersion(versions, raw.toString());
    }

    private void putVersion(Map<String, Integer> versions, String skillCode, Integer version) {
        String code = normalize(skillCode);
        if (blank(code) || version == null || version < 1) return;
        versions.merge(code, version, Math::max);
    }

    private Integer firstInt(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            Integer parsed = intValue(value);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    private List<String> mergeDistinct(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) merged.addAll(left);
        if (right != null) merged.addAll(right);
        return merged.stream().toList();
    }

    private boolean intersectsNormalized(List<String> left, List<String> right) {
        return !intersection(left, right).isEmpty();
    }

    private boolean containsNormalized(List<String> values, String value) {
        if (values == null || blank(value)) return false;
        String normalized = normalize(value);
        return values.stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private List<String> intersection(List<String> left, List<String> right) {
        if (left == null || right == null) return List.of();
        Set<String> rightSet = new LinkedHashSet<>(right.stream().map(this::normalize).filter(value -> !blank(value)).toList());
        return left.stream()
                .map(this::normalize)
                .filter(value -> !blank(value) && rightSet.contains(value))
                .distinct()
                .toList();
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return normalize(values.getFirst());
    }

    private String firstOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? normalize(fallback) : normalize(values.getFirst());
    }

    private boolean isOperation(String value) {
        String normalized = normalize(value);
        return Set.of("READ", "ANALYZE", "PROPOSE", "WRITE", "EXECUTE", "ANSWER").contains(normalized);
    }

    private boolean isToolPolicy(String value) {
        String normalized = normalize(value);
        return normalized != null && (normalized.endsWith("_ONLY") || normalized.endsWith("_ALLOWED") || normalized.equals("WRITE_WITH_APPROVAL"));
    }

    private Set<String> normalizeAgentIds(Set<String> agentIds) {
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

    private record RoutingCandidateSelection(CandidateFilterResult candidatePool, List<AgentCandidateScore> scores) { }

    private record CandidateFilterResult(
            List<AgentSnapshot> included,
            Set<String> reservationExcluded,
            Set<String> poisonExcluded,
            String targetPoolId,
            String targetPoolCode,
            String selectionStrategy,
            Map<String, AgentPoolRoutingMember> membersByAgentId,
            String poolBlockerCode) {
        static CandidateFilterResult blocked(String poolId, String poolCode, String blockerCode, Set<String> excluded) {
            return new CandidateFilterResult(List.of(), excluded == null ? Set.of() : excluded, Set.of(), poolId, poolCode,
                    "LOWEST_LOAD", Map.of(), blockerCode);
        }
        CandidateFilterResult withPool(String poolId, String poolCode, String strategy, Map<String, AgentPoolRoutingMember> members, String blockerCode) {
            return new CandidateFilterResult(included, reservationExcluded, poisonExcluded, poolId, poolCode,
                    strategy == null || strategy.isBlank() ? "LOWEST_LOAD" : strategy, members == null ? Map.of() : Map.copyOf(members), blockerCode);
        }
        private int memberCount() {
            return membersByAgentId == null ? 0 : membersByAgentId.size();
        }
        private int eligibleAgentCount() {
            return included == null ? 0 : included.size();
        }
        private String diagnostics() {
            StringBuilder builder = new StringBuilder();
            if (targetPoolId != null && !targetPoolId.isBlank()) {
                builder.append("; pool={targetPoolId=").append(targetPoolId)
                        .append(", targetPoolCode=").append(targetPoolCode)
                        .append(", selectionStrategy=").append(selectionStrategy)
                        .append(", memberCount=").append(memberCount())
                        .append(", eligibleAgentCount=").append(eligibleAgentCount());
                if (poolBlockerCode != null && !poolBlockerCode.isBlank()) {
                    builder.append(", blocker=").append(poolBlockerCode);
                }
                builder.append("}");
            }
            if (reservationExcluded != null && !reservationExcluded.isEmpty()) {
                builder.append("; excludedAfterReservationRace=").append(reservationExcluded);
            }
            if (poisonExcluded != null && !poisonExcluded.isEmpty()) {
                builder.append("; poisonAgentExcluded=").append(poisonExcluded);
            }
            return builder.toString();
        }
    }

    private record KnownSkillMatch(String domain, String taskType, String provider, String toolPolicy, String operation) {}


    private record V2RoutingComparison(
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

    private record BackendEligibilityScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure, List<String> requiredProfiles, List<String> approvedProfiles) {
        static BackendEligibilityScore notApplied() {
            return new BackendEligibilityScore(false, 0, 0, List.of(), List.of(),
                    "backend Dispatch Flow Agent Assignment eligibility service not available", false, List.of(), List.of());
        }
    }

    private record SkillScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure) {
        static SkillScore notApplied() {
            return new SkillScore(false, 0, 0, List.of(), List.of(), "skill-aware routing disabled or no known skill requirement", false);
        }
    }

    private record SkillVersionScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure) {
        static SkillVersionScore notApplied() {
            return new SkillVersionScore(false, 0, 0, List.of(), List.of(), "skill-version compatibility disabled or not requested", false);
        }
    }
}
