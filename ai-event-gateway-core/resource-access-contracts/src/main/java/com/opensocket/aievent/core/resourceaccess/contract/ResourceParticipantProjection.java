package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Server-derived participant projection. It is evidence, never the canonical business participant authority. */
public record ResourceParticipantProjection(
        String participantId,
        ResourceRef resourceRef,
        ResourceParticipantType participantType,
        String participantRefId,
        ResourceParticipantRole participantRole,
        VisibilityLevel visibilityLevel,
        List<String> allowedPermissionCodes,
        Instant validFrom,
        Instant validTo,
        DescriptorAuthority sourceAuthority,
        long sourceVersion,
        ResourceParticipantStatus status) {
    public ResourceParticipantProjection {
        participantId = required(participantId, "participantId");
        Objects.requireNonNull(resourceRef, "resourceRef");
        Objects.requireNonNull(participantType, "participantType");
        participantRefId = required(participantRefId, "participantRefId");
        Objects.requireNonNull(participantRole, "participantRole");
        Objects.requireNonNull(visibilityLevel, "visibilityLevel");
        allowedPermissionCodes = allowedPermissionCodes == null ? List.of() : allowedPermissionCodes.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().sorted().toList();
        Objects.requireNonNull(validFrom, "validFrom");
        if (validTo != null && !validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must be after validFrom");
        Objects.requireNonNull(sourceAuthority, "sourceAuthority");
        if (sourceVersion < 0) throw new IllegalArgumentException("sourceVersion must be non-negative");
        Objects.requireNonNull(status, "status");
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
