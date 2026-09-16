package com.opensocket.aievent.core.issuetracking.contract;

/** Provider-neutral mutation or verification requested for an external Issue projection. */
public enum IssueProjectionOperation {
    CREATE,
    UPDATE,
    COMMENT,
    TRANSITION,
    LINK_EXISTING,
    RELATE,
    VERIFY
}
