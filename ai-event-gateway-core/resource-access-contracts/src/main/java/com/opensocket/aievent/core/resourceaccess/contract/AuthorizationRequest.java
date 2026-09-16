package com.opensocket.aievent.core.resourceaccess.contract;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.util.Map;
import java.util.Objects;

/** Resource authorization input. It intentionally contains ResourceRef, not caller-supplied ResourceDescriptor. */
public record AuthorizationRequest(
        PrincipalRef principal,
        AuthenticationContext authenticationContext,
        TenantRef activeTenant,
        ResourceAction action,
        ResourceRef resourceRef,
        VisibilityLevel requestedVisibility,
        RequestChannel requestChannel,
        String purpose,
        OperationPhase operationPhase,
        String authorizationLeaseId,
        String correlationId,
        SecurityEpoch presentedEpoch,
        Map<String, String> trustedFlowContext) {
    public AuthorizationRequest {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(authenticationContext, "authenticationContext");
        Objects.requireNonNull(activeTenant, "activeTenant");
        if (!authenticationContext.principal().equals(principal)) throw new IllegalArgumentException("authentication principal and requested principal must match");
        if (!authenticationContext.activeTenant().equals(activeTenant)) throw new IllegalArgumentException("authentication tenant and active tenant must match");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceRef, "resourceRef");
        if (!activeTenant.tenantId().equals(resourceRef.tenantId())) throw new IllegalArgumentException("active tenant and resource tenant must match");
        requestedVisibility = requestedVisibility == null ? VisibilityLevel.NONE : requestedVisibility;
        Objects.requireNonNull(requestChannel, "requestChannel");
        purpose = requireText(purpose, "purpose");
        operationPhase = operationPhase == null ? OperationPhase.START : operationPhase;
        authorizationLeaseId = normalize(authorizationLeaseId);
        correlationId = requireText(correlationId, "correlationId");
        presentedEpoch = presentedEpoch == null ? SecurityEpoch.ZERO : presentedEpoch;
        trustedFlowContext = trustedFlowContext == null ? Map.of() : Map.copyOf(trustedFlowContext);
    }
    private static String requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
