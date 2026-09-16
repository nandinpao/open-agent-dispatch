package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Atomic participant snapshot associated with one authoritative participant version. */
public record ParticipantProjectionSnapshot(
        ResourceRef resourceRef,
        long participantVersion,
        List<ResourceParticipantProjection> participants,
        DescriptorAuthority sourceAuthority,
        String sourceEventId,
        Instant resolvedAt) {
    public ParticipantProjectionSnapshot {
        Objects.requireNonNull(resourceRef, "resourceRef");
        if (participantVersion < 0) throw new IllegalArgumentException("participantVersion must be non-negative");
        participants = participants == null ? List.of() : List.copyOf(participants);
        for (ResourceParticipantProjection participant : participants) {
            if (!resourceRef.equals(participant.resourceRef())) throw new IllegalArgumentException("participant resourceRef mismatch");
        }
        Objects.requireNonNull(sourceAuthority, "sourceAuthority");
        sourceEventId = sourceEventId == null ? "" : sourceEventId.trim();
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }
    public static ParticipantProjectionSnapshot empty(ResourceRef resourceRef, DescriptorAuthority authority, Instant at) {
        return new ParticipantProjectionSnapshot(resourceRef, 0, List.of(), authority, "", at);
    }
}
