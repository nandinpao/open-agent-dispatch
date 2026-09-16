package com.opensocket.aievent.core.integration.issue.policy;

/** Business policy outcome. This is separate from Projection, Outbox and Link lifecycle state. */
public enum IssuePolicyDecisionOutcome {
    NOT_REQUIRED,
    REQUIRED,
    MANUAL_DECISION
}
