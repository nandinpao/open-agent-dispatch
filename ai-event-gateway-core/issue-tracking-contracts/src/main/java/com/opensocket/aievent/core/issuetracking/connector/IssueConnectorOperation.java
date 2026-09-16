package com.opensocket.aievent.core.issuetracking.connector;

/** Connector operations are execution verbs, not OpenDispatch permission grants. */
public enum IssueConnectorOperation {
    READ,
    CREATE,
    COMMENT,
    UPDATE
}
