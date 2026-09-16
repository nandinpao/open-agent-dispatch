package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** Non-secret invitation lifecycle and delivery projection for account administration. */
public record UserInvitationStatusResponse(
        String userId,
        String status,
        String deliveryReference,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt,
        boolean canResend,
        boolean canRevoke,
        String deliveryMethod,
        String deliveryStatus,
        String deliveryFailureCode,
        String setupActionUrl) {

    public UserInvitationStatusResponse {
        deliveryMethod = deliveryMethod == null || deliveryMethod.isBlank() ? "NOT_ISSUED" : deliveryMethod;
        deliveryStatus = deliveryStatus == null || deliveryStatus.isBlank() ? "NOT_ISSUED" : deliveryStatus;
        deliveryFailureCode = deliveryFailureCode == null ? "" : deliveryFailureCode;
        setupActionUrl = setupActionUrl == null ? "" : setupActionUrl;
    }

    public static UserInvitationStatusResponse notIssued(
            String userId, String deliveryReference, boolean canResend) {
        return new UserInvitationStatusResponse(
                userId, "NOT_ISSUED", deliveryReference, "", null, null, canResend, false,
                "NOT_ISSUED", "NOT_ISSUED", "", "");
    }
}
