package com.opensocket.aievent.core.task.journey;

/** Small provider-neutral status vocabulary used by the TaskExecutionJourney read model. */
public enum TaskExecutionJourneyStatus {
    NOT_STARTED,
    NOT_APPLICABLE,
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    BLOCKED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CONFLICT
}
