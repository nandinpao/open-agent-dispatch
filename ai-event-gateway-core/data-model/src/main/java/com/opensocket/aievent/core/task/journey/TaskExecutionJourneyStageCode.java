package com.opensocket.aievent.core.task.journey;

/** Canonical operational stages for one Task execution journey. */
public enum TaskExecutionJourneyStageCode {
    INTAKE,
    ROUTING,
    ASSIGNMENT,
    DELIVERY,
    ACK,
    RESULT,
    ISSUE_POLICY,
    ISSUE_INTENT,
    ISSUE_MATERIALIZATION,
    OUTBOX,
    PROVIDER,
    READBACK,
    PROJECTION_SYNCED
}
