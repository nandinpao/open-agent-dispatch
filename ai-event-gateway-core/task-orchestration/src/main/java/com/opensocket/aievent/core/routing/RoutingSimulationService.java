package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

import java.util.List;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationCandidateView;
import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationRequest;
import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationResponse;
import com.opensocket.aievent.core.routing.flow.FlowResolution;
import com.opensocket.aievent.core.routing.authority.DispatchDecisionEngine;
import com.opensocket.aievent.core.routing.cutover.GenericAuthoritativeRoutingResult;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.domain.TaskSeverity;

/**
 * No-side-effect Dispatch Simulation using the same production routing components as assignment routing.
 *
 * <p>This service intentionally calls FlowResolver, RuleResolver/FlowRuleRoutingService,
 * PoolResolver, RuntimeEligibilityEvaluator and SelectionStrategy through RoutingDecisionService's
 * current routing boundary, but it never creates or persists Task, Assignment, Delivery, ACK, or Result
 * records.  The Admin UI uses this as Phase 5D's "test this dispatch setup" contract.</p>
 */
@Service
public class RoutingSimulationService {
    private final RoutingDecisionService routingDecisionService;
    private final DispatchDecisionEngine dispatchDecisionEngine;

    public RoutingSimulationService(RoutingDecisionService routingDecisionService,
                                    DispatchDecisionEngine dispatchDecisionEngine) {
        this.routingDecisionService = routingDecisionService;
        this.dispatchDecisionEngine = dispatchDecisionEngine;
    }

    public DispatchSimulationResponse simulate(DispatchSimulationRequest request) {
        DispatchSimulationRequest normalized = normalizeRequest(request);
        TaskRecord task = taskFrom(normalized);
        DispatchSimulationResponse response = baseResponse(normalized);
        boolean draftSimulation = "DRAFT_SIMULATION".equals(normalized.getEvaluationMode());
        response.getDiagnostics().put("routingModel", "AGENT_POOL_FIRST");
        response.getDiagnostics().put("evaluationMode", normalized.getEvaluationMode());
        response.getDiagnostics().put("draftFlowVisible", draftSimulation);
        response.getDiagnostics().put("sideEffectContract", "NO_TASK_NO_ASSIGNMENT_NO_DELIVERY_NO_ACK_NO_RESULT");
        response.getDiagnostics().put("productionResolvers", List.of(
                "FlowResolver",
                "RuleResolver",
                "FlowRuleRoutingService",
                "PoolResolver",
                "RuntimeEligibilityEvaluator",
                "SelectionStrategyRegistry"));

        if (blank(normalized.getTenantId())) {
            return blocked(response, "TENANT_ID_REQUIRED", "tenantId is required for Dispatch Simulation.");
        }
        if (blank(normalized.getSourceSystem())) {
            return blocked(response, "SOURCE_SYSTEM_REQUIRED", "sourceSystem is required for Dispatch Simulation.");
        }
        if (draftSimulation && blank(normalized.getFlowId())) {
            return blocked(response, "FLOW_ID_REQUIRED_FOR_DRAFT_SIMULATION", "flowId is required when evaluationMode=DRAFT_SIMULATION.");
        }
        if (!routingDecisionService.properties().isAssignmentEnabled()) {
            return blocked(response, "ASSIGNMENT_ROUTING_DISABLED", "Assignment routing is disabled by ROUTING_ASSIGNMENT_ENABLED=false.");
        }

        FlowResolution flowResolution = draftSimulation
                ? routingDecisionService.flowResolver().resolveSimulation(task, normalized.getAttributes())
                : routingDecisionService.flowResolver().resolve(task);
        TaskRecord routedTask = flowResolution.task();
        response.setMatchedFlowId(routedTask == null ? null : routedTask.getMatchedFlowId());
        response.setFlowVersion(flowResolution.flowVersion());
        response.setMatchedRuleId(routedTask == null ? null : routedTask.getMatchedRuleId());
        response.setResolutionType(routedTask == null ? null : routedTask.getRoutingPath());
        response.setTargetPoolId(routedTask == null ? null : firstNonBlank(routedTask.getTargetPoolId(), routedTask.getAssignedPoolId()));

        if (!flowResolution.flowRuleTask() || !flowResolution.sourceFlowPoolFirstTask()) {
            response.getDiagnostics().put("flowRuleTask", flowResolution.flowRuleTask());
            response.getDiagnostics().put("sourceFlowPoolFirstTask", flowResolution.sourceFlowPoolFirstTask());
            return blocked(response, "SOURCE_FLOW_NOT_MATCHED", "No deterministic Flow Rule matched this simulation payload. A0-R3 canonical behavior is NO_MATCH -> Triage; a Source default Pool is legacy execution compatibility, not Flow Match authority.");
        }

        GenericAuthoritativeRoutingResult decision = dispatchDecisionEngine.decide(routedTask, Set.of());
        response.getDiagnostics().put("dispatchAuthority", "DISPATCH_DECISION_ENGINE");
        response.getDiagnostics().put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        response.getDiagnostics().put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        response.getDiagnostics().put("runtimeReportedCapabilitiesAuthority", false);
        response.getDiagnostics().put("canonicalDecisionStatus", decision == null || decision.status() == null ? null : decision.status().name());
        response.getDiagnostics().put("canonicalReasonCode", decision == null ? null : decision.reasonCode());

        if (decision == null) {
            return blocked(response, "CANONICAL_DISPATCH_AUTHORITY_UNAVAILABLE", "Canonical DispatchDecisionEngine returned no decision.");
        }

        if (decision.requirement() != null) {
            Object targetPoolId = decision.requirement().getEvidence().get("targetPoolId");
            Object targetPoolCode = decision.requirement().getEvidence().get("targetPoolCode");
            Object selectionStrategy = decision.requirement().getEvidence().get("selectionStrategy");
            response.setTargetPoolId(firstNonBlank(targetPoolId, response.getTargetPoolId()));
            response.setTargetPoolCode(targetPoolCode == null ? null : targetPoolCode.toString());
            response.setSelectionStrategy(selectionStrategy == null
                    ? (decision.requirement().getRoutingStrategy() == null ? null : decision.requirement().getRoutingStrategy().name())
                    : selectionStrategy.toString());
            response.getDiagnostics().put("requiredCapabilities", decision.requirement().getRequiredCapabilities());
            response.getDiagnostics().put("requirementReasonCode", decision.requirement().getReasonCode());
        }

        List<AgentCandidateScore> scores = decision.candidates();
        List<GenericAuthoritativeRoutingResult.BlockedCandidateEvidence> blocked = decision.blockedCandidates();
        response.setPoolMemberCount(scores.size() + blocked.size());
        response.setCandidateAgentCount(scores.size() + blocked.size());
        response.setEligibleAgentCount(scores.size());

        List<DispatchSimulationCandidateView> candidateEvidence = scores.stream().map(score -> candidate(score, false)).toList();
        if (decision.selected() != null) {
            response.setSelectedAgentId(decision.selected().agentId());
            candidateEvidence.stream()
                    .filter(candidate -> decision.selected().agentId().equals(candidate.getAgentId()))
                    .findFirst().ifPresent(candidate -> candidate.setSelected(true));
        }
        response.setCandidateEvidence(candidateEvidence);

        List<DispatchSimulationCandidateView> blockedCandidates = blocked.stream()
                .map(this::blockedCandidate)
                .toList();
        response.setBlockedCandidates(blockedCandidates);

        if (decision.status() == GenericAuthoritativeRoutingResult.Status.MANUAL_REVIEW) {
            response.setManualOnly(true);
            response.setDispatchable(false);
            response.setStatus("MANUAL_ASSIGNMENT_REQUIRED");
            response.setBlockerCode(firstNonBlank(decision.reasonCode(), "MANUAL_ASSIGNMENT_REQUIRED"));
            response.setBlockerReason(firstNonBlank(decision.reason(), "This Agent Pool requires manual assignment."));
            response.setSummary("Manual assignment pending. Canonical authority resolved the Flow and Pool, but this Pool does not auto-select an Agent.");
            return response;
        }
        if (!decision.hasSelection()) {
            return blocked(response, firstNonBlank(decision.reasonCode(), "NO_CANONICAL_ELIGIBLE_AGENT"),
                    firstNonBlank(decision.reason(), "No Agent Pool member passed canonical eligibility."));
        }
        response.setDispatchable(true);
        response.setStatus("READY");
        response.setSummary("Canonical dispatch simulation passed: Flow -> Agent Pool -> Core-approved Capability -> Runtime Eligibility -> Routing Score selected Agent "
                + response.getSelectedAgentId() + ". No Task, Assignment or Delivery was created.");
        return response;
    }

    private DispatchSimulationResponse baseResponse(DispatchSimulationRequest request) {
        DispatchSimulationResponse response = new DispatchSimulationResponse();
        response.setTenantId(request.getTenantId());
        response.setSourceSystem(request.getSourceSystem());
        response.setEventStage(firstNonBlank(request.getEventStage(), "EXTERNAL"));
        response.setObjectType(request.getObjectType());
        response.setEventType(request.getEventType());
        response.setErrorCode(request.getErrorCode());
        response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        response.setEvaluationMode(firstNonBlank(request.getEvaluationMode(), "DRAFT_SIMULATION"));
        response.setSideEffectFree(true);
        response.setCreatedArtifacts(List.of());
        return response;
    }

    private DispatchSimulationResponse blocked(DispatchSimulationResponse response, String code, String reason) {
        response.setDispatchable(false);
        response.setStatus("BLOCKED");
        response.setBlockerCode(code);
        response.setBlockerReason(reason);
        response.setSummary(reason);
        return response;
    }

    private DispatchSimulationCandidateView candidate(AgentCandidateScore score, boolean blocked) {
        DispatchSimulationCandidateView view = new DispatchSimulationCandidateView();
        view.setAgentId(score.agentId());
        view.setStatus(score.status());
        view.setScore(score.score());
        view.setEligible(!blocked);
        view.setReason(score.reason());
        view.setScoreBreakdown(score.scoreBreakdown());
        if (score.scoreBreakdown() != null && Boolean.TRUE.equals(score.scoreBreakdown().get("blockingFailure"))) {
            view.setEligible(false);
            view.setBlockingReasons(List.of("ROUTING_SCORE_BLOCKED"));
        }
        return view;
    }

    private DispatchSimulationCandidateView blockedCandidate(GenericAuthoritativeRoutingResult.BlockedCandidateEvidence blocked) {
        DispatchSimulationCandidateView view = new DispatchSimulationCandidateView();
        view.setAgentId(blocked.agentId());
        view.setStatus(blocked.runtimeStatus());
        view.setEligible(false);
        view.setBlockingReasons(blocked.reasonCodes());
        view.setReason(blocked.reasonCodes().isEmpty() ? "CANONICAL_ELIGIBILITY_BLOCKED" : String.join(", ", blocked.reasonCodes()));
        return view;
    }

    private TaskRecord taskFrom(DispatchSimulationRequest request) {
        TaskRecord task = new TaskRecord();
        task.setTaskId("simulation-" + UUID.randomUUID());
        task.setTenantId(request.getTenantId());
        task.setSourceSystem(request.getSourceSystem());
        task.setOriginSourceSystem(request.getOriginSourceSystem());
        task.setTargetSystem(request.getTargetSystem());
        task.setEventStage(firstNonBlank(request.getEventStage(), "EXTERNAL"));
        task.setObjectType(request.getObjectType());
        task.setEventType(request.getEventType());
        task.setErrorCode(request.getErrorCode());
        task.setSeverity(parseSeverity(request.getSeverity()));
        task.setSiteId(request.getSiteId());
        task.setPlantId(request.getPlantId());
        task.setMatchedFlowId(request.getFlowId());
        task.setClassificationStatus("CLASSIFIED");
        task.setRequiredCapabilities(List.of());
        task.setCreatedReason("DISPATCH_SIMULATION_NO_SIDE_EFFECT");
        return task;
    }

    private DispatchSimulationRequest normalizeRequest(DispatchSimulationRequest request) {
        DispatchSimulationRequest normalized = request == null ? new DispatchSimulationRequest() : request;
        normalized.setTenantId(trim(normalized.getTenantId()));
        normalized.setFlowId(trim(normalized.getFlowId()));
        normalized.setSourceSystem(trim(normalized.getSourceSystem()));
        normalized.setOriginSourceSystem(trim(normalized.getOriginSourceSystem()));
        normalized.setTargetSystem(trim(normalized.getTargetSystem()));
        normalized.setEventStage(firstNonBlank(trim(normalized.getEventStage()), "EXTERNAL"));
        normalized.setObjectType(wildcardToNull(normalized.getObjectType()));
        normalized.setEventType(wildcardToNull(normalized.getEventType()));
        normalized.setErrorCode(wildcardToNull(normalized.getErrorCode()));
        String mode = firstNonBlank(trim(normalized.getEvaluationMode()), "DRAFT_SIMULATION").toUpperCase(java.util.Locale.ROOT);
        if (!"DRAFT_SIMULATION".equals(mode) && !"RUNTIME_READINESS".equals(mode)) {
            throw new IllegalArgumentException("evaluationMode must be DRAFT_SIMULATION or RUNTIME_READINESS");
        }
        normalized.setEvaluationMode(mode);
        return normalized;
    }

    private TaskSeverity parseSeverity(String value) {
        if (blank(value)) return TaskSeverity.MEDIUM;
        try {
            return TaskSeverity.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TaskSeverity.MEDIUM;
        }
    }

    private String wildcardToNull(String value) {
        String trimmed = trim(value);
        return "*".equals(trimmed) ? null : trimmed;
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
