package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Map;
import java.util.Objects;

/** Business adapter request for one resource authorization checkpoint. */
public record ResourceEnforcementCommand(
        ResourceAction action,
        ResourceRef resourceRef,
        VisibilityLevel requestedVisibility,
        RequestChannel requestChannel,
        String purpose,
        OperationPhase operationPhase,
        SecurityEpoch presentedEpoch,
        Map<String, String> trustedFlowContext) {
    public ResourceEnforcementCommand {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceRef, "resourceRef");
        requestedVisibility = requestedVisibility == null ? VisibilityLevel.NONE : requestedVisibility;
        Objects.requireNonNull(requestChannel, "requestChannel");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose is required");
        purpose = purpose.trim();
        operationPhase = operationPhase == null ? OperationPhase.START : operationPhase;
        presentedEpoch = presentedEpoch == null ? SecurityEpoch.ZERO : presentedEpoch;
        trustedFlowContext = trustedFlowContext == null ? Map.of() : Map.copyOf(trustedFlowContext);
    }
}
