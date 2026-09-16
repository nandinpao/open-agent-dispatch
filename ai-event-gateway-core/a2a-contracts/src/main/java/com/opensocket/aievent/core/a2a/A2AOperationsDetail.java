package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Composite, read-only A2A operational view. It intentionally exposes only data-minimized
 * evidence summaries; canonical domain objects, payload references, and token hashes stay server-side.
 */
public record A2AOperationsDetail(
        A2AOperationsListItem summary,
        List<StageStatus> stages,
        List<TimelineEntry> timeline,
        BlockerDiagnosis blocker,
        List<GovernedAction> actions,
        AuthorityEvidence request,
        AuthorityEvidence result,
        AuthorityEvidence cancellation,
        AuthorityEvidence reconciliationCase,
        AuthorityEvidence quarantine,
        AuthorityEvidence aggregation,
        A2AOperationsTopology topology) {

    public A2AOperationsDetail(A2AOperationsListItem summary, List<StageStatus> stages, List<TimelineEntry> timeline,
            BlockerDiagnosis blocker, List<GovernedAction> actions, AuthorityEvidence request,
            AuthorityEvidence result, AuthorityEvidence cancellation, AuthorityEvidence reconciliationCase,
            AuthorityEvidence quarantine, AuthorityEvidence aggregation) {
        this(summary, stages, timeline, blocker, actions, request, result, cancellation, reconciliationCase,
                quarantine, aggregation, new A2AOperationsTopology(List.of(), List.of()));
    }

    public record StageStatus(String stage, String status, String authority, String evidenceReference,
                              OffsetDateTime updatedAt) {}

    public record TimelineEntry(String eventId, String stage, String eventType, String status,
                                String reasonCode, String message, String actor, String evidenceReference,
                                OffsetDateTime occurredAt) {}

    /** Safe evidence summary for the browser. Secrets, payload references, and token hashes are excluded. */
    public record AuthorityEvidence(String authority, String evidenceType, String evidenceId, String status,
                                    Long version, OffsetDateTime occurredAt, Map<String, String> facts) {
        public AuthorityEvidence {
            facts = facts == null ? Map.of() : Map.copyOf(facts);
        }
    }

    public record BlockerDiagnosis(String code, String title, String explanation, String evidence,
                                   String recommendedAction, boolean humanDecisionRequired) {
        public static BlockerDiagnosis none() {
            return new BlockerDiagnosis("NONE", "No active blocker",
                    "The A2A chain has no diagnosed operational blocker.", null, null, false);
        }
    }

    public record GovernedAction(String code, String label, String method, String endpoint,
                                 String requiredPermission, boolean confirmationRequired,
                                 String guardrail) {}
}
