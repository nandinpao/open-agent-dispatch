package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;

/**
 * Tenant onboarding result. One-time setup URLs are returned only for an explicit MANUAL delivery
 * and must be rendered once with no-store semantics; token plaintext is never persisted.
 */
public record UserOnboardingResponse(
        UserResponse user,
        MembershipResponse tenantMembership,
        List<MembershipResponse> departmentMemberships,
        List<MembershipResponse> groupMemberships,
        List<RoleBindingResponse> roleBindings,
        boolean invitationIssued,
        boolean setupIssued,
        boolean temporaryPasswordConfigured,
        String authenticationMethod,
        String setupDeliveryMethod,
        String setupDeliveryStatus,
        String setupDeliveryReference,
        String setupDeliveryId,
        Instant setupExpiresAt,
        String setupFailureCode,
        String setupActionUrl,
        List<String> requiredActions) {

    public UserOnboardingResponse {
        departmentMemberships = List.copyOf(departmentMemberships);
        groupMemberships = List.copyOf(groupMemberships);
        roleBindings = List.copyOf(roleBindings);
        authenticationMethod = authenticationMethod == null ? "LOCAL" : authenticationMethod;
        setupDeliveryMethod = setupDeliveryMethod == null ? "NOT_REQUIRED" : setupDeliveryMethod;
        setupDeliveryStatus = setupDeliveryStatus == null ? "NOT_ISSUED" : setupDeliveryStatus;
        setupDeliveryReference = setupDeliveryReference == null ? "" : setupDeliveryReference;
        setupDeliveryId = setupDeliveryId == null ? "" : setupDeliveryId;
        setupFailureCode = setupFailureCode == null ? "" : setupFailureCode;
        setupActionUrl = setupActionUrl == null ? "" : setupActionUrl;
        requiredActions = List.copyOf(requiredActions);
    }
}
