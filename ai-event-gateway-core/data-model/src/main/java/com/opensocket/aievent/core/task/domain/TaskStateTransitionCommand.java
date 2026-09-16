package com.opensocket.aievent.core.task.domain;

import java.time.OffsetDateTime;
import com.opensocket.aievent.core.task.TaskStatus;

public record TaskStateTransitionCommand(
        String tenantId,
        String taskId,
        long expectedVersion,
        TaskStatus newStatus,
        String reasonCode,
        String reason,
        TaskActorType actorType,
        String actorId,
        String correlationId,
        String idempotencyKey,
        OffsetDateTime transitionAt) {
}
