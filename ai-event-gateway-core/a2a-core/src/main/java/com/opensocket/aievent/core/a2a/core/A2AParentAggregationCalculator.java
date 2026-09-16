package com.opensocket.aievent.core.a2a.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import com.opensocket.aievent.core.a2a.*;

/** Deterministic, re-runnable parent aggregation calculation. */
public final class A2AParentAggregationCalculator {
    public A2AParentAggregation calculate(String tenantId, String parentTaskId,
            A2AResultAggregationPolicy policy, int quorumCount, long policyVersion, String policySnapshotHash,
            List<A2ARequest> requests, List<A2AResult> results, String lastResultId) {
        List<A2ARequest> values = requests == null ? List.of() : List.copyOf(requests);
        List<A2AResult> resultValues = results == null ? List.of() : List.copyOf(results);
        int partial = (int) resultValues.stream().filter(v -> v.getResultStatus() == A2AResultStatus.PARTIAL).count();
        int succeeded = Math.max(0, count(values, A2ARequestStatus.COMPLETED) - partial);
        int failed = count(values, A2ARequestStatus.FAILED) + count(values, A2ARequestStatus.REJECTED)
                + count(values, A2ARequestStatus.EXPIRED);
        int cancelled = count(values, A2ARequestStatus.CANCELLED_CONFIRMED);
        int pending = Math.max(0, values.size() - succeeded - partial - failed - cancelled);
        A2AResultAggregationPolicy effective = (policy == null ? A2AResultAggregationPolicy.MANUAL_DECISION : policy).canonical();
        int effectiveQuorum = Math.max(1, quorumCount);
        Decision decision = decide(effective, values.size(), pending, succeeded, partial, failed, cancelled, effectiveQuorum);
        String summary = "total=" + values.size() + ",results=" + resultValues.size() + ",pending=" + pending
                + ",succeeded=" + succeeded + ",partial=" + partial + ",failed=" + failed + ",cancelled=" + cancelled;
        A2AParentAggregation aggregation = new A2AParentAggregation();
        aggregation.setTenantId(tenantId);
        aggregation.setParentTaskId(parentTaskId);
        aggregation.setAggregationPolicy(effective);
        aggregation.setPolicyVersion(policyVersion);
        aggregation.setPolicySnapshotHash(policySnapshotHash);
        aggregation.setQuorumCount(effectiveQuorum);
        aggregation.setAggregateStatus(decision.status());
        aggregation.setDecisionReason(decision.reason());
        aggregation.setManualDecisionRequired(decision.manual());
        aggregation.setResultCount(resultValues.size());
        aggregation.setTotalCount(values.size());
        aggregation.setPendingCount(pending);
        aggregation.setSucceededCount(succeeded);
        aggregation.setPartialCount(partial);
        aggregation.setFailedCount(failed);
        aggregation.setCancelledCount(cancelled);
        aggregation.setSummary(summary);
        aggregation.setComputationHash(hash(parentTaskId + "|" + effective + "|" + effectiveQuorum + "|"
                + policyVersion + "|" + policySnapshotHash + "|" + summary + "|" + decision.status()));
        aggregation.setLastResultId(lastResultId);
        return aggregation;
    }

    /** Compatibility overload used by older focused tests. */
    public A2AParentAggregation calculate(String tenantId, String parentTaskId,
            A2AResultAggregationPolicy policy, List<A2ARequest> requests, List<A2AResult> results, String lastResultId) {
        return calculate(tenantId, parentTaskId, policy, 1, 0L, null, requests, results, lastResultId);
    }

    public A2AParentAggregation calculate(String tenantId, String parentTaskId,
            A2AResultAggregationPolicy policy, List<A2ARequest> requests, String lastResultId) {
        return calculate(tenantId, parentTaskId, policy, 1, 0L, null, requests, List.of(), lastResultId);
    }

    private int count(List<A2ARequest> values, A2ARequestStatus status) {
        return (int) values.stream().filter(v -> v.getRequestStatus() == status).count();
    }

    private Decision decide(A2AResultAggregationPolicy policy, int total, int pending, int succeeded,
            int partial, int failed, int cancelled, int quorum) {
        if (total == 0) return new Decision("PENDING", "No child requests exist", false);
        return switch (policy) {
            case ANY_SUCCESS -> succeeded + partial > 0
                    ? new Decision("SUCCESS", "At least one child produced a usable result", false)
                    : pending > 0 ? new Decision("PENDING", "Waiting for the first usable result", false)
                    : new Decision("FAILURE", "No child produced a usable result", false);
            case ALL_SUCCESS -> pending > 0
                    ? new Decision("PENDING", "Waiting for every child result", false)
                    : failed + cancelled + partial == 0
                            ? new Decision("SUCCESS", "Every child succeeded", false)
                            : new Decision("FAILURE", "At least one child did not fully succeed", false);
            case QUORUM -> succeeded + partial >= Math.min(quorum, total)
                    ? new Decision(partial > 0 ? "PARTIAL" : "SUCCESS", "Configured quorum was reached", false)
                    : pending > 0 ? new Decision("PENDING", "Configured quorum has not been reached", false)
                    : new Decision("FAILURE", "Configured quorum can no longer be reached", false);
            case PARTIAL_ALLOWED -> pending > 0
                    ? new Decision("PENDING", "Waiting for remaining child results", false)
                    : succeeded + partial > 0
                            ? new Decision(failed + cancelled + partial > 0 ? "PARTIAL" : "SUCCESS",
                                    "Usable partial completion is allowed", false)
                            : new Decision("FAILURE", "No usable child result exists", false);
            case FAIL_FAST -> failed + cancelled > 0
                    ? new Decision("FAILURE", "A child failure triggered fail-fast aggregation", false)
                    : pending > 0 ? new Decision("PENDING", "No failure yet; waiting for remaining children", false)
                    : new Decision(partial > 0 ? "PARTIAL" : "SUCCESS", "All terminal children are acceptable", false);
            case MANUAL_DECISION -> new Decision("WAIT_HUMAN", "Policy requires an explicit human decision", true);
            default -> throw new IllegalStateException("Aggregation policy must be canonical: " + policy);
        };
    }

    private String hash(String value) {
        try { return "agg-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private record Decision(String status, String reason, boolean manual) {}
}
