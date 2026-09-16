package com.opensocket.aievent.core.capability;

import java.util.List;

/** Current normalized capability-delegation runtime receipt. */
public record CapabilityDelegationReceipt(
        String delegationId,
        String status,
        String parentTaskId,
        String childTaskId,
        String assignmentId,
        String dispatchRequestId,
        String authorizationDecisionId,
        String routingDecisionId,
        String adapterResolutionId,
        List<String> reasonCodes) {
    public CapabilityDelegationReceipt {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
