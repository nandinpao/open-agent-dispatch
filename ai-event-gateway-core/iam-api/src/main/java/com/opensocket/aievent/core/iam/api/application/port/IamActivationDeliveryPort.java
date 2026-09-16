package com.opensocket.aievent.core.iam.api.application.port;

import java.time.Instant;

/**
 * Account-activation delivery boundary.
 *
 * <p>The command may carry a one-time secret in memory. Implementations MUST NOT persist or log
 * the secret or the resulting setup URL. Only non-secret delivery receipt metadata may be stored.</p>
 */
public interface IamActivationDeliveryPort {
    DeliveryReceipt deliver(DeliveryCommand command);

    record DeliveryCommand(
            String tenantId,
            String userId,
            String tokenId,
            String purpose,
            String deliveryMethod,
            String recipientReference,
            String oneTimeSecret,
            Instant expiresAt,
            String actorId,
            String correlationId) { }

    /** setupActionUrl is populated only for an explicit MANUAL handoff and must be shown once. */
    record DeliveryReceipt(
            String deliveryId,
            String deliveryMethod,
            String deliveryStatus,
            String recipientReference,
            Instant issuedAt,
            Instant expiresAt,
            String failureCode,
            String setupActionUrl) {
        public DeliveryReceipt {
            failureCode = failureCode == null ? "" : failureCode;
            setupActionUrl = setupActionUrl == null ? "" : setupActionUrl;
        }
    }
}
