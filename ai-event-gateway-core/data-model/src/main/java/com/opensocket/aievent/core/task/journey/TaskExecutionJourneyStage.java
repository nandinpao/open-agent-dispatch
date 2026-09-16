package com.opensocket.aievent.core.task.journey;

import java.time.OffsetDateTime;
import java.util.List;

/** One evidence-backed operational stage. */
public record TaskExecutionJourneyStage(
        TaskExecutionJourneyStageCode stage,
        TaskExecutionJourneyStatus status,
        boolean required,
        String reasonCode,
        String summary,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime lastChangedAt,
        List<TaskExecutionEvidenceRef> evidenceRefs,
        TaskExecutionRetryability retryability,
        OffsetDateTime retryAfter,
        String authority,
        long revision) {
    public TaskExecutionJourneyStage {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        retryability = retryability == null ? TaskExecutionRetryability.UNKNOWN : retryability;
        revision = Math.max(0L, revision);
    }
}
