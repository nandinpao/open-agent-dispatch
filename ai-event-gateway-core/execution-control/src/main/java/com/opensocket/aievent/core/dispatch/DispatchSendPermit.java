package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable Core-side authority token proving that a dispatch passed the final pre-send admission.
 *
 * <p>The permit is intentionally not a Netty authority object. Core creates it only after the
 * authoritative lease/fence/envelope/intent state has been revalidated and, for A0-R7, after the
 * durable DispatchIntent has atomically entered {@code SEND_STARTED}. Network transport may proceed
 * only while this permit is held by the current execution call.</p>
 */
public record DispatchSendPermit(
        String permitId,
        String tenantId,
        String dispatchRequestId,
        String assignmentId,
        String taskId,
        int attemptNo,
        String authorityVersion,
        String leaseId,
        Long fencingToken,
        String envelopeId,
        String bindingId,
        String intentId,
        String externalExecutionRef,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt) {

    public static DispatchSendPermit legacy(DispatchRequest request, OffsetDateTime issuedAt) {
        return new DispatchSendPermit(
                "send-permit-" + UUID.randomUUID(),
                request == null ? null : request.getTenantId(),
                request == null ? null : request.getDispatchRequestId(),
                request == null ? null : request.getAssignmentId(),
                request == null ? null : request.getTaskId(),
                request == null ? 0 : request.getAttemptCount(),
                "LEGACY",
                null,
                null,
                null,
                null,
                null,
                request == null || request.getDispatchRequestId() == null
                        ? null
                        : "dispatch:" + request.getDispatchRequestId(),
                issuedAt,
                request == null ? null : request.getClaimUntil());
    }
}
