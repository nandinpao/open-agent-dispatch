package com.opensocket.aievent.core.task;

import java.time.Duration;

public record TaskLifecyclePolicy(
        boolean timeoutEnabled,
        boolean autoReassignEnabled,
        Duration createdTimeout,
        Duration assignedTimeout,
        Duration dispatchedTimeout,
        Duration runningTimeout,
        int maxReassignments,
        int maxBatchSize) {
    public TaskLifecyclePolicy {
        createdTimeout = createdTimeout == null ? Duration.ofMinutes(10) : createdTimeout;
        assignedTimeout = assignedTimeout == null ? Duration.ofMinutes(10) : assignedTimeout;
        dispatchedTimeout = dispatchedTimeout == null ? Duration.ofMinutes(10) : dispatchedTimeout;
        runningTimeout = runningTimeout == null ? Duration.ofMinutes(30) : runningTimeout;
        maxReassignments = Math.max(0, maxReassignments);
        maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000));
    }
}
