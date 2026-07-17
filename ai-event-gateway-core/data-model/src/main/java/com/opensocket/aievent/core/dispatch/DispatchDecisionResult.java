package com.opensocket.aievent.core.dispatch;

/** Stable result contract returned by the execution-control adapter to task orchestration. */
public record DispatchDecisionResult(
        boolean dispatchRequestCreated,
        String dispatchRequestId,
        String dispatchStatus,
        String reviewMode,
        String eligibilityStatus,
        String gatewayDispatchPath,
        String reason
) {
    public static DispatchDecisionResult none(String reason) {
        return new DispatchDecisionResult(false, null, null, null, null, null, reason);
    }
}
