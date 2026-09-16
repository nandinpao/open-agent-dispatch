package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;
import com.opensocket.aievent.core.routing.flow.FlowResolution;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Orchestrates the current routing decision flow while RoutingDecisionService
 * remains the public API and persistence/observation facade.
 *
 * <p>Phase 3-7 intentionally preserves behavior: Source Flow -> Pool ->
 * runtime eligibility -> selection, MANUAL_ONLY, NO_CANDIDATE, SELECTED
 * reasons, save side effects, and observation taxonomy remain delegated through
 * the existing service methods.</p>
 */
class RoutingOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(RoutingOrchestrator.class);

    private final RoutingDecisionService service;

    RoutingOrchestrator(RoutingDecisionService service) {
        this.service = service;
    }

    RoutingDecisionRecord decide(TaskRecord task, Set<String> excludedAgentIds) {
        Set<String> excluded = service.normalizeAgentIds(excludedAgentIds);
        FlowResolution flowResolution = service.flowResolver().resolve(task);
        task = java.util.Objects.requireNonNull(flowResolution.task(), "Resolved routing task must not be null");
        RoutingPolicy policy = flowResolution.policy();
        log.info("routing_decision_started taskId={} incidentId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} matchedFlowId={} matchedRuleId={} routingPath={} targetPoolId={} assignedPoolId={} policy={} excludedAgents={} routingModel=AGENT_POOL_FIRST",
                task == null ? null : task.getTaskId(), task == null ? null : task.getIncidentId(), task == null ? null : task.getTenantId(),
                task == null ? null : task.getSourceSystem(), task == null ? null : task.getEventStage(), task == null ? null : task.getObjectType(),
                task == null ? null : task.getEventType(), task == null ? null : task.getErrorCode(), task == null ? null : task.getClassificationStatus(),
                task == null ? null : task.getMatchedFlowId(), task == null ? null : task.getMatchedRuleId(), task == null ? null : task.getRoutingPath(),
                task == null ? null : task.getTargetPoolId(), task == null ? null : task.getAssignedPoolId(), policy, excluded.size());
        RoutingDecisionRecord decision = new RoutingDecisionRecord();
        decision.setDecisionId("route-" + UUID.randomUUID());
        decision.setTaskId(task.getTaskId());
        decision.setTenantId(task.getTenantId());
        decision.setIncidentId(task.getIncidentId());
        decision.setRoutingPolicy(policy);
        decision.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (!service.properties().isAssignmentEnabled()) {
            decision.setStatus(RoutingDecisionStatus.SUPPRESSED);
            decision.setDecisionReason("Assignment routing is disabled by ROUTING_ASSIGNMENT_ENABLED=false");
            log.info("routing_decision_suppressed taskId={} policy={} reason={}", task.getTaskId(), policy, decision.getDecisionReason());
            return service.saveAndRecord(decision);
        }
        boolean authoritativePoolTask = service.isAuthoritativePoolTask(task);
        if (service.properties().isZeroSpecialCaseRuntimeEnabled()
                && service.properties().isFlowRuleRoutingEnabled() && !service.properties().isFlowRuleLegacyFallbackEnabled()
                && !service.isFlowRuleTask(task) && !authoritativePoolTask) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("NO_ACTIVE_FLOW_RULE: new work requires a matched Dispatch Flow Rule; legacy profile/source fallback is disabled");
            log.warn("routing_new_work_fail_closed_no_flow_rule taskId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} reason=NO_ACTIVE_FLOW_RULE legacyFallback=false",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode());
            return service.saveAndRecord(decision);
        }
        if (service.requiresManualReview(policy)) {
            decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
            decision.setDecisionReason("Routing policy " + policy + " requires human review before assignment");
            log.info("routing_decision_manual_review taskId={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} requestedSkill={}",
                    task.getTaskId(), policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill());
            return service.saveAndRecord(decision);
        }

        if (authoritativePoolTask) {
            String authority = service.isGovernedPoolTask(task) ? "GOVERNED_POOL" : "SOURCE_FLOW";
            log.info("routing_authoritative_pool_entering_canonical_engine taskId={} tenantId={} sourceSystem={} routingPath={} matchedFlowId={} matchedRuleId={} a2aPolicyId={} targetPoolId={} authority={} candidateAuthority=AGENT_POOL_MEMBERSHIP capabilityAuthority=CORE_APPROVED_AGENT_CAPABILITY runtimeReportedCapabilitiesAuthority=false",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getRoutingPath(),
                    task.getMatchedFlowId(), task.getMatchedRuleId(), task.getA2aPolicyId(),
                    firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()), authority);
            return service.authoritativePoolRoutingBridge().decide(task, excluded, decision, policy);
        }

        RoutingDecisionRecord genericDecision = service.genericAuthorityBridge().decide(task, excluded, decision, service.isFlowRuleTask(task));
        if (genericDecision != null) {
            return genericDecision;
        }

        // Compatibility/fallback-only scorer. Current Source Flow and governed-pool work
        // returns through the canonical engine above, where Core-approved Capability
        // assignments are blocking authority. Runtime-reported capability metadata must
        // never be treated as qualification for those current product paths.
        EligibilityEngineMode eligibilityMode = EligibilityEngineMode.SHADOW;
        RoutingDecisionService.V2RoutingComparison v2Comparison = RoutingDecisionService.V2RoutingComparison.notApplied(eligibilityMode);

        RoutingDecisionService.RoutingCandidateSelection candidateSelection = service.selectCandidates(task, excluded, policy, v2Comparison, eligibilityMode);
        CandidateFilterResult candidatePool = candidateSelection.candidatePool();
        List<AgentCandidateScore> scores = candidateSelection.scores();
        decision.setCandidates(scores);
        if (service.isManualOnlyPool(candidatePool)) {
            decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
            decision.setRoutingPolicy(RoutingPolicy.MANUAL_REVIEW);
            decision.setDecisionReason(service.routingEvidenceBuilder().manualOnlyDecisionReason(candidatePool, service.flowRuleDecisionSuffix(task)));
            log.info("routing_manual_only_pool taskId={} tenantId={} sourceSystem={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} selectionStrategy=MANUAL_ONLY selectionStrategyContract=SUPPORTED_POOL_STRATEGY",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                    candidatePool.targetPoolId(), candidatePool.targetPoolCode());
            return service.saveAndRecord(decision);
        }
        if (scores.isEmpty()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            service.applyUserFacingError(decision, eligibilityMode.enforce() && v2Comparison.applied()
                    ? service.userFacingV2EnforcementError(task, v2Comparison)
                    : service.routingBlockerResolver().noCandidateError(task, candidatePool));
            log.warn("routing_no_candidate taskId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} poolMemberCount={} eligibleAgentCount={} poolBlocker={} reservationExcluded={} poisonExcluded={} userFacingErrorCode={} routingModel=AGENT_POOL_FIRST",
                    task.getTaskId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode(), task.getClassificationStatus(),
                    policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                    candidatePool == null ? task.getTargetPoolId() : candidatePool.targetPoolId(), candidatePool == null ? null : candidatePool.targetPoolCode(),
                    candidatePool == null ? 0 : candidatePool.memberCount(), candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                    candidatePool == null ? null : candidatePool.poolBlockerCode(),
                    candidatePool == null ? Set.of() : candidatePool.reservationExcluded(), candidatePool == null ? Set.of() : candidatePool.poisonExcluded(),
                    decision.getUserFacingError() == null ? null : decision.getUserFacingError().getCode());
            return service.saveAndRecord(decision);
        }
        if (eligibilityMode.enforce() && service.properties().isRequireV2ScoreBreakdownInEnforce()) {
            List<String> missingV2Explainability = scores.stream()
                    .filter(score -> !service.hasRequiredV2ScoreBreakdown(score))
                    .map(AgentCandidateScore::agentId)
                    .toList();
            if (!missingV2Explainability.isEmpty()) {
                decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
                service.applyUserFacingError(decision, service.userFacingV2ScoreBreakdownRequiredError(task, missingV2Explainability));
                return service.saveAndRecord(decision);
            }
        }
        AgentCandidateScore selected = scores.getFirst();
        if (selected.score() < service.properties().getMinimumScore()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            service.applyUserFacingError(decision, service.routingBlockerResolver().belowMinimumError(task, selected, candidatePool, service.properties()));
            log.warn("routing_below_minimum taskId={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} requestedSkill={} selectedAgentId={} selectedScore={} minimumScore={} missingCapabilities={}",
                    task.getTaskId(), policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(),
                    selected.agentId(), selected.score(), service.properties().getMinimumScore(), selected.missingCapabilities());
            return service.saveAndRecord(decision);
        }
        decision.setStatus(RoutingDecisionStatus.SELECTED);
        decision.setSelectedAgentId(selected.agentId());
        decision.setSelectedGatewayNodeId(selected.ownerGatewayNodeId());
        decision.setSelectedAgentSessionId(selected.agentSessionId());
        decision.setSelectedSiteId(selected.siteId());
        decision.setSelectedScore(selected.score());
        decision.setDecisionReason(service.routingEvidenceBuilder().selectedDecisionReason(
                policy,
                selected,
                candidatePool,
                service.flowRuleDecisionSuffix(task),
                service.v2DecisionSuffix(v2Comparison, eligibilityMode, selected.agentId())));
        log.info("routing_selected taskId={} decisionId={} tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} classificationStatus={} policy={} routingPath={} matchedFlowId={} matchedRuleId={} targetPoolId={} targetPoolCode={} poolMemberCount={} eligibleAgentCount={} selectedAgentId={} selectedScore={} candidateCount={} routingModel=AGENT_POOL_FIRST",
                task.getTaskId(), decision.getDecisionId(), task.getTenantId(), task.getSourceSystem(), task.getEventStage(), task.getObjectType(), task.getEventType(), task.getErrorCode(), task.getClassificationStatus(),
                policy, task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(),
                candidatePool == null ? task.getTargetPoolId() : candidatePool.targetPoolId(), candidatePool == null ? null : candidatePool.targetPoolCode(),
                candidatePool == null ? 0 : candidatePool.memberCount(), candidatePool == null ? scores.size() : candidatePool.eligibleAgentCount(),
                selected.agentId(), selected.score(), scores.size());
        return service.saveAndRecord(decision);
    }



    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
