package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Objects;

public record ResourceProjectionEvent(String eventId, ResourceRef resourceRef, String eventType,
        String sourceEventId, String payloadHash, Instant occurredAt) {
    public ResourceProjectionEvent {
        eventId = required(eventId, "eventId"); Objects.requireNonNull(resourceRef, "resourceRef");
        eventType = required(eventType, "eventType"); sourceEventId = sourceEventId == null ? "" : sourceEventId.trim();
        payloadHash = required(payloadHash, "payloadHash"); Objects.requireNonNull(occurredAt, "occurredAt");
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.trim();
    }
}
