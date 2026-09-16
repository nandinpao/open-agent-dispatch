package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ParticipantProjectionSnapshot;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import java.time.Instant;
import java.util.Objects;

/** One local transaction for descriptor, participants, ownership history, orphan state and outbox evidence. */
public record ResourceProjectionBatch(ResourceDescriptor descriptor, ParticipantProjectionSnapshot participants,
        ResourceProjectionEvent event, String sourceEventId, Instant projectedAt) {
    public ResourceProjectionBatch {
        Objects.requireNonNull(descriptor, "descriptor"); Objects.requireNonNull(participants, "participants");
        if (!descriptor.resourceRef().equals(participants.resourceRef())) throw new IllegalArgumentException("projection resource mismatch");
        Objects.requireNonNull(event, "event");
        if (!descriptor.resourceRef().equals(event.resourceRef())) throw new IllegalArgumentException("projection event resource mismatch");
        sourceEventId = sourceEventId == null ? "" : sourceEventId.trim(); Objects.requireNonNull(projectedAt, "projectedAt");
    }
}
