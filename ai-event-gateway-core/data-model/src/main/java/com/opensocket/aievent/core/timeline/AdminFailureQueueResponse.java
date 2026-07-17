package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * TODO 15-E Admin Failure Queue response.
 */
public record AdminFailureQueueResponse(
        OffsetDateTime generatedAt,
        int total,
        Map<String, Integer> counts,
        Map<String, Integer> reasonCategoryCounts,
        Map<String, Integer> dispatchErrorCounts,
        List<AdminFailureQueueItem> items
) {
    public AdminFailureQueueResponse(OffsetDateTime generatedAt,
                                     int total,
                                     Map<String, Integer> counts,
                                     List<AdminFailureQueueItem> items) {
        this(generatedAt, total, counts, Map.of(), Map.of(), items);
    }
}
