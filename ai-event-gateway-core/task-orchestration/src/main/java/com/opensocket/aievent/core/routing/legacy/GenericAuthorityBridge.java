package com.opensocket.aievent.core.routing.legacy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverDecision;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverService;
import com.opensocket.aievent.core.routing.cutover.GenericAuthoritativeRoutingResult;
import com.opensocket.aievent.core.routing.cutover.GenericDispatchAuthoritativeService;
import com.opensocket.aievent.core.routing.evidence.RoutingEvidenceBuilder;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Isolates the legacy / generic authoritative routing bridge from the current
 * Source Flow -> Agent Pool path.
 *
 * <p>Phase 3-9 intentionally preserves behavior: P11 fail-closed semantics,
 * non-authoritative manual hold, cutover outcome recording, generic authority
 * decision reasons, and candidate trace logging remain the same. The current
 * Pool-first path should only cross this boundary through RoutingOrchestrator
 * when a task is not recognized as Source Flow Pool-first work.</p>
 */
public class GenericAuthorityBridge {
    private static final Logger log = LoggerFactory.getLogger(GenericAuthorityBridge.class);

    private final RoutingProperties properties;
    private final DispatchCutoverService dispatchCutoverService;
    private final GenericDispatchAuthoritativeService genericAuthoritativeService;
    private final RoutingEvidenceBuilder routingEvidenceBuilder;
    private final Function<RoutingDecisionRecord, RoutingDecisionRecord> decisionWriter;

    public GenericAuthorityBridge(RoutingProperties properties,
                                  DispatchCutoverService dispatchCutoverService,
                                  GenericDispatchAuthoritativeService genericAuthoritativeService,
                                  RoutingEvidenceBuilder routingEvidenceBuilder,
                                  Function<RoutingDecisionRecord, RoutingDecisionRecord> decisionWriter) {
        this.properties = properties;
        this.dispatchCutoverService = dispatchCutoverService;
        this.genericAuthoritativeService = genericAuthoritativeService;
        this.routingEvidenceBuilder = routingEvidenceBuilder == null ? new RoutingEvidenceBuilder() : routingEvidenceBuilder;
        this.decisionWriter = decisionWriter;
    }

    public RoutingDecisionRecord decide(TaskRecord task,
                                        Set<String> excluded,
                                        RoutingDecisionRecord decision,
                                        boolean flowRuleTask) {
        if (task == null || !flowRuleTask) {
            return null;
        }
        if (genericAuthoritativeService == null || dispatchCutoverService == null
                || properties == null || !properties.isGenericAuthoritativeEnabled()) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("P11 generic authority is required for new Flow work and is unavailable");
            return save(decision);
        }
        DispatchCutoverDecision cutover;
        try {
            cutover = dispatchCutoverService.decide(task);
        } catch (RuntimeException ex) {
            decision.setStatus(RoutingDecisionStatus.NO_CANDIDATE);
            decision.setDecisionReason("P11 cutover decision failed closed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return save(decision);
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
            return save(decision);
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
        List<Map<String, Object>> candidateTrace = routingEvidenceBuilder.candidateTrace(result.candidates());
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
        return save(decision);
    }

    private RoutingDecisionRecord save(RoutingDecisionRecord decision) {
        return decisionWriter == null ? decision : decisionWriter.apply(decision);
    }
}
