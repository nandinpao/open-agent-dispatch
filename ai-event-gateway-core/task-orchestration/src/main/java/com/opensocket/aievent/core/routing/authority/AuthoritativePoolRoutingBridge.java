package com.opensocket.aievent.core.routing.authority;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.DispatchUserFacingErrorCode;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.cutover.GenericAuthoritativeRoutingResult;
import com.opensocket.aievent.core.routing.evidence.RoutingEvidenceBuilder;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Adapter from the canonical {@link DispatchDecisionEngine} to the persisted
 * RoutingDecisionRecord used by task assignment.
 *
 * <p>This bridge contains no candidate lookup or eligibility logic. It exists so
 * Source Flow and governed Pool work cannot fall back to the legacy scorer after
 * the canonical engine has become authoritative.</p>
 */
public class AuthoritativePoolRoutingBridge {
    private static final Logger log = LoggerFactory.getLogger(AuthoritativePoolRoutingBridge.class);

    private final DispatchDecisionEngine dispatchDecisionEngine;
    private final RoutingEvidenceBuilder evidenceBuilder;
    private final Function<RoutingDecisionRecord, RoutingDecisionRecord> decisionWriter;

    public AuthoritativePoolRoutingBridge(DispatchDecisionEngine dispatchDecisionEngine,
                                          RoutingEvidenceBuilder evidenceBuilder,
                                          Function<RoutingDecisionRecord, RoutingDecisionRecord> decisionWriter) {
        this.dispatchDecisionEngine = dispatchDecisionEngine;
        this.evidenceBuilder = evidenceBuilder == null ? new RoutingEvidenceBuilder() : evidenceBuilder;
        this.decisionWriter = decisionWriter;
    }

    public RoutingDecisionRecord decide(TaskRecord task,
                                        Set<String> excludedAgentIds,
                                        RoutingDecisionRecord decision,
                                        RoutingPolicy policy) {
        if (dispatchDecisionEngine == null) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("CANONICAL_DISPATCH_AUTHORITY_UNAVAILABLE: DispatchDecisionEngine bean is unavailable");
            decision.setUserFacingError(error(task, "CANONICAL_DISPATCH_AUTHORITY_UNAVAILABLE", decision.getDecisionReason()));
            return save(decision);
        }

        GenericAuthoritativeRoutingResult result = dispatchDecisionEngine.decide(
                task, excludedAgentIds == null ? Set.of() : excludedAgentIds);
        decision.setRoutingPolicy(policy == null ? RoutingPolicy.FLOW_RULE : policy);
        decision.setCandidates(result == null || result.candidates() == null ? java.util.List.of() : result.candidates());

        if (result == null) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("CANONICAL_DISPATCH_RESULT_MISSING: DispatchDecisionEngine returned no result");
            decision.setUserFacingError(error(task, "CANONICAL_DISPATCH_RESULT_MISSING", decision.getDecisionReason()));
            return save(decision);
        }

        switch (result.status()) {
            case SELECTED -> applySelected(decision, result);
            case MANUAL_REVIEW -> {
                decision.setStatus(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
                decision.setRoutingPolicy(RoutingPolicy.MANUAL_REVIEW);
                decision.setDecisionReason("Canonical dispatch authority requires manual review: " + result.reasonCode()
                        + ": " + result.reason());
            }
            case REQUIREMENT_BLOCKED, NO_CANDIDATE, ERROR -> {
                decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
                decision.setDecisionReason("Canonical dispatch authority fail-closed: " + result.reasonCode()
                        + ": " + result.reason());
                decision.setUserFacingError(error(task, result.reasonCode(), result.reason()));
            }
        }

        if (decision.getSelectedAgentId() == null) {
            log.warn("canonical_pool_dispatch_no_selection tenantId={} taskId={} routingPath={} poolId={} status={} reasonCode={} reason={} candidates={}",
                    task == null ? null : task.getTenantId(), task == null ? null : task.getTaskId(),
                    task == null ? null : task.getRoutingPath(), task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()),
                    result.status(), result.reasonCode(), result.reason(), evidenceBuilder.candidateTrace(result.candidates()));
        } else {
            log.info("canonical_pool_dispatch_selected tenantId={} taskId={} routingPath={} poolId={} selectedAgentId={} selectedScore={} reasonCode={} candidates={}",
                    task == null ? null : task.getTenantId(), task == null ? null : task.getTaskId(),
                    task == null ? null : task.getRoutingPath(), task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()),
                    decision.getSelectedAgentId(), decision.getSelectedScore(), result.reasonCode(), evidenceBuilder.candidateTrace(result.candidates()));
        }
        return save(decision);
    }

    private void applySelected(RoutingDecisionRecord decision, GenericAuthoritativeRoutingResult result) {
        AgentCandidateScore selected = result.selected();
        if (selected == null) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("CANONICAL_SELECTED_CANDIDATE_MISSING: SELECTED result did not contain an Agent");
            return;
        }
        decision.setStatus(RoutingDecisionStatus.SELECTED);
        decision.setSelectedAgentId(selected.agentId());
        decision.setSelectedGatewayNodeId(selected.ownerGatewayNodeId());
        decision.setSelectedAgentSessionId(selected.agentSessionId());
        decision.setSelectedSiteId(selected.siteId());
        decision.setSelectedScore(selected.score());
        decision.setDecisionReason("Canonical dispatch authority: " + result.reason()
                + "; reasonCode=" + result.reasonCode()
                + "; candidateAuthority=AGENT_POOL_MEMBERSHIP"
                + "; capabilityAuthority=CORE_APPROVED_AGENT_CAPABILITY");
    }

    private DispatchUserFacingError error(TaskRecord task, String reasonCode, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("taskId", task == null ? null : task.getTaskId());
        context.put("sourceSystem", task == null ? null : task.getSourceSystem());
        context.put("targetPoolId", task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()));
        Map<String, Object> technical = new LinkedHashMap<>();
        technical.put("reasonCode", reasonCode);
        technical.put("reason", reason);
        technical.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        technical.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        technical.put("runtimeReportedCapabilitiesAuthority", false);
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_ELIGIBILITY_V2_BLOCKED,
                "HIGH",
                "No Agent Pool member passed canonical dispatch eligibility.",
                "Check Agent Pool membership, Core-approved Required Capabilities, Agent approval/credential, runtime readiness and capacity.",
                "runbooks/dispatch/canonical-pool-authority",
                context,
                technical);
    }

    private RoutingDecisionRecord save(RoutingDecisionRecord decision) {
        return decisionWriter == null ? decision : decisionWriter.apply(decision);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
