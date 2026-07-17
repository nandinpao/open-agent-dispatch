package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.task.TaskRecord;

/**
 * TODO 15-E task dispatch timeline response.
 */
public record DispatchTimelineResponse(
        String taskId,
        TaskRecord task,
        OffsetDateTime generatedAt,
        Map<String, Integer> counts,
        List<DispatchTimelineEvent> events
) {
}
