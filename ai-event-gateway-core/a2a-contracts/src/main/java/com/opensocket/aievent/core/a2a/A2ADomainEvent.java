package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.Map;

public record A2ADomainEvent(
        String eventId,
        A2ADomainEventType eventType,
        String tenantId,
        String aggregateId,
        String rootTaskId,
        String correlationId,
        String causationId,
        String actorType,
        String actorId,
        OffsetDateTime occurredAt,
        int payloadVersion,
        Map<String,Object> payload) {}
