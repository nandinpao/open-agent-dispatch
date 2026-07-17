package com.opensocket.aievent.core.task;

/** Platform lifecycle categories only. Business task codes are persisted data. */
public enum TaskType {
    TRIAGE,
    RESOLUTION,
    INCIDENT_RESPONSE,
    INCIDENT_ESCALATION
}
