package com.opensocket.aievent.core.assignment;

import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;

public record AssignmentDecisionResult(
        boolean assignmentCreated,
        String assignmentId,
        String selectedAgentId,
        String selectedGatewayNodeId,
        String selectedAgentSessionId,
        String selectedSiteId,
        String routingDecisionId,
        String assignmentStatus,
        String reason,
        boolean dispatchRequestCreated,
        String dispatchRequestId,
        String dispatchStatus,
        String dispatchReviewMode,
        String dispatchEligibilityStatus,
        String dispatchGatewayPath,
        String dispatchReason
) {
    public static AssignmentDecisionResult none(String reason) {
        return new AssignmentDecisionResult(false, null, null, null, null, null, null, null, reason,
                false, null, null, null, null, null, null);
    }

    public static AssignmentDecisionResult withDispatch(boolean assignmentCreated,
                                                        String assignmentId,
                                                        String selectedAgentId,
                                                        String selectedGatewayNodeId,
                                                        String selectedAgentSessionId,
                                                        String selectedSiteId,
                                                        String routingDecisionId,
                                                        String assignmentStatus,
                                                        String reason,
                                                        DispatchDecisionResult dispatch) {
        DispatchDecisionResult d = dispatch == null ? DispatchDecisionResult.none("Dispatch request not evaluated") : dispatch;
        return new AssignmentDecisionResult(assignmentCreated, assignmentId, selectedAgentId, selectedGatewayNodeId, selectedAgentSessionId, selectedSiteId,
                routingDecisionId, assignmentStatus, reason,
                d.dispatchRequestCreated(), d.dispatchRequestId(), d.dispatchStatus(), d.reviewMode(), d.eligibilityStatus(), d.gatewayDispatchPath(), d.reason());
    }
}
