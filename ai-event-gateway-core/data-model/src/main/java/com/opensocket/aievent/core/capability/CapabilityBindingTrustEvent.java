package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Append-only Phase 2 trust lifecycle evidence for a Capability Binding. */
public record CapabilityBindingTrustEvent(
        String tenantId,
        String eventId,
        String bindingId,
        String fromStatus,
        String toStatus,
        String reason,
        String actorRef,
        OffsetDateTime occurredAt) {
}
