package com.opensocket.aievent.core.issue.observability;

/** Canonical Route B observability stages. Order is part of the API contract. */
public enum IssueRuntimeStageCode {
    POLICY,
    BINDING,
    ACTION,
    EXECUTOR,
    PROVIDER,
    RESULT,
    LINK
}
