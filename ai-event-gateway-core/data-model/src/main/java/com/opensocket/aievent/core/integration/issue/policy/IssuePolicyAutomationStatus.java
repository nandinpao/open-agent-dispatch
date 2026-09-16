package com.opensocket.aievent.core.integration.issue.policy;

/** Orchestration status only. Downstream Projection/Outbox/Link states remain authoritative for their own lifecycles. */
public enum IssuePolicyAutomationStatus {
    NOT_REQUIRED,
    WAITING_MANUAL_DECISION,
    BINDING_BLOCKED,
    INTENT_READY,
    ACTION_REQUESTED,
    MATERIALIZED,
    FAILED
}
