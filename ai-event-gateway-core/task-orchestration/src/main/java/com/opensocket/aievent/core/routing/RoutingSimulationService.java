package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationCandidateView;
import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationRequest;
import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationResponse;
import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;
import com.opensocket.aievent.core.routing.flow.FlowResolution;
import com.opensocket.aievent.core.task.TaskRecord;

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

    public RoutingSimulationService(RoutingDecisionService routingDecisionService) {
        this.routingDecisionService = routingDecisionService;
    }

    public DispatchSimulationResponse simulate(DispatchSimulationRequest request) {
        DispatchSimulationRequest normalized = normalizeRequest(request);
        TaskRecord task = taskFrom(normalized);
        DispatchSimulationResponse response = baseResponse(normalized);
        response.getDiagnostics().put("routingModel", "AGENT_POOL_FIRST");
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
        if (!routingDecisionService.properties().isAssignmentEnabled()) {
            return blocked(response, "ASSIGNMENT_ROUTING_DISABLED", "Assignment routing is disabled by ROUTING_ASSIGNMENT_ENABLED=false.");
        }

        FlowResolution flowResolution = routingDecisionService.flowResolver().resolve(task);
        TaskRecord routedTask = flowResolution.task();
        response.setMatchedFlowId(routedTask == null ? null : routedTask.getMatchedFlowId());
        response.setMatchedRuleId(routedTask == null ? null : routedTask.getMatchedRuleId());
        response.setResolutionType(routedTask == null ? null : routedTask.getRoutingPath());
        response.setTargetPoolId(routedTask == null ? null : firstNonBlank(routedTask.getTargetPoolId(), routedTask.getAssignedPoolId()));

        if (!flowResolution.flowRuleTask() || !flowResolution.sourceFlowPoolFirstTask()) {
            response.getDiagnostics().put("flowRuleTask", flowResolution.flowRuleTask());
            response.getDiagnostics().put("sourceFlowPoolFirstTask", flowResolution.sourceFlowPoolFirstTask());
            return blocked(response, "SOURCE_FLOW_NOT_MATCHED", "No active Source Flow / Default Pool or Rule target Pool matched this simulation payload.");
        }

        RoutingDecisionService.RoutingCandidateSelection selection = routingDecisionService.selectCandidates(
                routedTask,
                Set.of(),
                flowResolution.policy(),
                RoutingDecisionService.V2RoutingComparison.notApplied(EligibilityEngineMode.SHADOW),
                EligibilityEngineMode.SHADOW);
        CandidateFilterResult pool = selection.candidatePool();
        List<AgentCandidateScore> scores = selection.scores() == null ? List.of() : selection.scores();

        response.setTargetPoolId(pool == null ? response.getTargetPoolId() : firstNonBlank(pool.targetPoolId(), response.getTargetPoolId()));
        response.setTargetPoolCode(pool == null ? null : pool.targetPoolCode());
        response.setSelectionStrategy(pool == null ? null : pool.selectionStrategy());
        response.setPoolMemberCount(pool == null ? 0 : pool.memberCount());
        response.setCandidateAgentCount(pool == null ? 0 : pool.memberCount());
        response.setEligibleAgentCount(scores.size());
        response.getDiagnostics().put("poolBlockerCode", pool == null ? null : pool.poolBlockerCode());
        response.getDiagnostics().put("reservationExcluded", pool == null ? List.of() : pool.reservationExcluded());
        response.getDiagnostics().put("poisonExcluded", pool == null ? List.of() : pool.poisonExcluded());

        List<DispatchSimulationCandidateView> candidateEvidence = scores.stream().map(score -> candidate(score, false)).toList();
        if (!candidateEvidence.isEmpty()) {
            candidateEvidence.getFirst().setSelected(true);
            response.setSelectedAgentId(candidateEvidence.getFirst().getAgentId());
        }
        response.setCandidateEvidence(candidateEvidence);

        List<DispatchSimulationCandidateView> blockedCandidates = new ArrayList<>();
        if (pool != null) {
            for (String agentId : pool.reservationExcluded()) {
                blockedCandidates.add(blockedCandidate(agentId, "RESERVATION_EXCLUDED"));
            }
            for (String agentId : pool.poisonExcluded()) {
                blockedCandidates.add(blockedCandidate(agentId, "POOL_AGENT_BACKOFF"));
            }
            if (candidateEvidence.isEmpty() && !blank(pool.poolBlockerCode())) {
                DispatchSimulationCandidateView poolBlocker = new DispatchSimulationCandidateView();
                poolBlocker.setAgentId("__POOL__");
                poolBlocker.setEligible(false);
                poolBlocker.setBlockingReasons(List.of(pool.poolBlockerCode()));
                poolBlocker.setReason("Pool-level blocker: " + pool.poolBlockerCode());
                blockedCandidates.add(poolBlocker);
            }
        }
        response.setBlockedCandidates(blockedCandidates);

        if (routingDecisionService.isManualOnlyPool(pool)) {
            response.setManualOnly(true);
            response.setDispatchable(false);
            response.setStatus("MANUAL_ASSIGNMENT_REQUIRED");
            response.setBlockerCode("MANUAL_ASSIGNMENT_REQUIRED");
            response.setBlockerReason("This Agent Pool uses MANUAL_ONLY. No Agent is selected by simulation or automatic routing.");
            response.setSummary("模擬完成：此工作池需要人工指定 Agent，不會自動建立 Assignment。需要正式事件時，Task 會進入人工指定狀態。");
            return response;
        }
        if (candidateEvidence.isEmpty()) {
            String blocker = pool == null || blank(pool.poolBlockerCode()) ? "NO_ELIGIBLE_AGENT_IN_POOL" : pool.poolBlockerCode();
            return blocked(response, blocker, "No eligible Agent was available in the resolved Agent Pool.");
        }
        response.setDispatchable(true);
        response.setStatus("READY");
        response.setSummary("模擬完成：已解析 Source Flow、Rule / Default Pool、Agent Pool 與 Runtime Eligibility，預計選擇 Agent " + response.getSelectedAgentId() + "。此結果不會建立 Task、Assignment 或 Delivery。");
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

    private DispatchSimulationCandidateView blockedCandidate(String agentId, String reason) {
        DispatchSimulationCandidateView view = new DispatchSimulationCandidateView();
        view.setAgentId(agentId);
        view.setEligible(false);
        view.setBlockingReasons(List.of(reason));
        view.setReason(reason);
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
        return normalized;
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
