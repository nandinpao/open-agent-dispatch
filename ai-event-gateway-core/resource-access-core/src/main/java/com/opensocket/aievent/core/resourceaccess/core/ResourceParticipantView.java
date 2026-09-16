package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;
import java.util.List;

public record ResourceParticipantView(
        String participantId, String participantType, String participantRefId,
        String participantRole, String visibilityLevel, List<String> allowedPermissions,
        String status, Instant validFrom, Instant validTo) {
    public ResourceParticipantView { allowedPermissions = allowedPermissions == null ? List.of() : List.copyOf(allowedPermissions); }
}
