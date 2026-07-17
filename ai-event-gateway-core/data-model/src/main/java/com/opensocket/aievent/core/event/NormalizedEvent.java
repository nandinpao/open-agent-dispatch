package com.opensocket.aievent.core.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record NormalizedEvent(
        String eventId,
        String tenantId,
        String sourceSystem,
        String eventStage,
        String originSourceSystem,
        String targetSystem,
        String siteId,
        String plantId,
        String objectType,
        String objectId,
        String eventType,
        String errorCode,
        String requestedSkill,
        String handoffMode,
        String correlationId,
        String parentTaskId,
        EventSeverity severity,
        String normalizedMessage,
        OffsetDateTime occurredAt,
        Map<String, Object> attributes
) {
}
