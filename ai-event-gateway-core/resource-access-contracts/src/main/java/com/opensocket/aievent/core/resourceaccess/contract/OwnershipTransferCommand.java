package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Mutation command routed to the canonical Domain ownership authority. */
public record OwnershipTransferCommand(
        ResourceRef resourceRef,
        String newOwnerDepartmentId,
        String newOwnerGroupId,
        String newStewardUserId,
        long expectedResourceVersion,
        String reason,
        String actorId,
        String correlationId,
        String idempotencyKey,
        Instant requestedAt) {
    public OwnershipTransferCommand {
        Objects.requireNonNull(resourceRef, "resourceRef");
        newOwnerDepartmentId = normalize(newOwnerDepartmentId);
        newOwnerGroupId = normalize(newOwnerGroupId);
        newStewardUserId = normalize(newStewardUserId);
        if (newOwnerDepartmentId.isEmpty() && newOwnerGroupId.isEmpty() && newStewardUserId.isEmpty())
            throw new IllegalArgumentException("at least one new owner is required");
        if (expectedResourceVersion < 1) throw new IllegalArgumentException("expectedResourceVersion must be positive");
        reason = required(reason, "reason"); actorId = required(actorId, "actorId");
        correlationId = required(correlationId, "correlationId"); idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
