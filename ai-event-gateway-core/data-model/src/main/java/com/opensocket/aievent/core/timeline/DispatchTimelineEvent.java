package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * TODO 15-E canonical dispatch timeline row for Admin UI / audit inspection.
 */
public record DispatchTimelineEvent(
        int sequence,
        OffsetDateTime occurredAt,
        String stage,
        String action,
        String status,
        String severity,
        String source,
        String message,
        Map<String, String> references,
        Map<String, Object> details
) {
}
