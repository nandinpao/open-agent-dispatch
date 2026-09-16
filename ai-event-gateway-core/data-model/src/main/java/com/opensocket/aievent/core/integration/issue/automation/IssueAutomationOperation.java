package com.opensocket.aievent.core.integration.issue.automation;

/**
 * Provider operation requested by canonical Task Issue automation.
 *
 * <p>This is an operation vocabulary, not an OpenDispatch permission model. The
 * configured Issue provider remains the final authority for whether the operation
 * is allowed.</p>
 */
public enum IssueAutomationOperation {
    CREATE_ISSUE,
    UPDATE_ISSUE,
    ADD_COMMENT,
    TRANSITION_ISSUE,
    READ_ISSUE
}
