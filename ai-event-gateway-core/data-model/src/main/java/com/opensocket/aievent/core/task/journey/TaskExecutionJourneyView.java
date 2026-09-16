package com.opensocket.aievent.core.task.journey;

import java.time.OffsetDateTime;
import java.util.List;

/** Rebuildable operational read model. No field in this view is a write authority. */
public record TaskExecutionJourneyView(
        String tenantId,
        String taskId,
        String rootTaskId,
        String parentTaskId,
        String correlationId,
        TaskExecutionJourneyStatus status,
        TaskExecutionJourneyStageCode currentStage,
        String currentReasonCode,
        long revision,
        OffsetDateTime generatedAt,
        List<TaskExecutionJourneyStage> stages) {
    public TaskExecutionJourneyView {
        stages = stages == null ? List.of() : List.copyOf(stages);
        revision = Math.max(0L, revision);
    }
}
