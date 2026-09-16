package com.opensocket.aievent.core.task.v53;

import com.opensocket.aievent.core.task.TaskStatus;

/**
 * Read-only projection of the v5.3 three-axis task state from the current
 * authoritative {@link TaskStatus}.
 */
public record TaskStateSnapshot(
        TaskStatus authoritativeStatus,
        TaskLifecycle lifecycle,
        TaskPhase phase,
        TaskOutcome outcome,
        String projectionVersion) {
}
