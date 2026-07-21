package com.opensocket.aievent.core.routing.evidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;

/**
 * Builds routing evidence and stable decision reason text for the current
 * Source Flow -> Agent Pool routing path.
 *
 * <p>Phase 3-6 extracts evidence assembly out of RoutingDecisionService while
 * preserving existing scoreBreakdown keys and decision-reason wording.</p>
 */
public final class RoutingEvidenceBuilder {

    public List<Map<String, Object>> candidateTrace(List<AgentCandidateScore> candidates) {
        return (candidates == null ? List.<AgentCandidateScore>of() : candidates).stream()
                .map(this::candidateTrace)
                .toList();
    }

    public Map<String, Object> candidateTrace(AgentCandidateScore candidate) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (candidate == null) {
            return trace;
        }
        trace.put("agentId", candidate.agentId());
        trace.put("score", candidate.score());
        trace.put("status", candidate.status());
        trace.put("matchedCapabilities", candidate.matchedCapabilities());
        trace.put("missingCapabilities", candidate.missingCapabilities());
        trace.put("reason", candidate.reason());
        trace.put("scoreBreakdown", candidate.scoreBreakdown());
        return trace;
    }

    public String manualOnlyDecisionReason(CandidateFilterResult candidatePool, String flowRuleDecisionSuffix) {
        return "MANUAL_ASSIGNMENT_REQUIRED: Agent Pool "
                + firstNonBlank(candidatePool == null ? null : candidatePool.targetPoolCode(),
                candidatePool == null ? null : candidatePool.targetPoolId(),
                "UNKNOWN_POOL")
                + " uses MANUAL_ONLY; no automatic Agent assignment or Netty delivery will be created"
                + (candidatePool == null ? "" : candidatePool.diagnostics())
                + nullToEmpty(flowRuleDecisionSuffix);
    }

    public String selectedDecisionReason(RoutingPolicy policy,
                                         AgentCandidateScore selected,
                                         CandidateFilterResult candidatePool,
                                         String flowRuleDecisionSuffix,
                                         String v2DecisionSuffix) {
        return "Selected by " + policy + ": " + (selected == null ? null : selected.reason())
                + (candidatePool == null ? "" : candidatePool.diagnostics())
                + nullToEmpty(flowRuleDecisionSuffix)
                + nullToEmpty(v2DecisionSuffix);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
