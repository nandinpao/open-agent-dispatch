package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;

/** Non-secret sign-in readiness projection for Tenant administrators. */
public record UserAuthenticationReadinessResponse(
        String tenantId,
        String userId,
        String authenticationMethod,
        String accountStatus,
        String signInState,
        boolean signInReady,
        String passwordState,
        Instant passwordChangedAt,
        Instant passwordExpiresAt,
        String mfaState,
        String mfaType,
        Instant mfaVerifiedAt,
        long recoveryCodesRemaining,
        Instant lastSuccessfulLogin,
        long failedLoginCount,
        Instant lockedUntil,
        long activeSessionCount,
        String setupState,
        Instant setupExpiresAt,
        String deliveryMethod,
        String deliveryStatus,
        String deliveryReference,
        String deliveryFailureCode,
        List<String> blockingActions) {
    public UserAuthenticationReadinessResponse {
        authenticationMethod = authenticationMethod == null || authenticationMethod.isBlank() ? "LOCAL" : authenticationMethod;
        signInState = signInState == null || signInState.isBlank() ? "SETUP_REQUIRED" : signInState;
        deliveryMethod = deliveryMethod == null || deliveryMethod.isBlank() ? "NOT_ISSUED" : deliveryMethod;
        deliveryStatus = deliveryStatus == null || deliveryStatus.isBlank() ? "NOT_ISSUED" : deliveryStatus;
        deliveryReference = deliveryReference == null ? "" : deliveryReference;
        deliveryFailureCode = deliveryFailureCode == null ? "" : deliveryFailureCode;
        blockingActions = blockingActions == null ? List.of() : List.copyOf(blockingActions);
    }
}
