package com.opensocket.aievent.core.action.executor.issue;

public enum IssueVendor {
    /**
     * Local/test/e2e only. Production issue actions must use a concrete vendor.
     */
    @Deprecated(forRemoval = true)
    MOCK,
    JIRA,
    REDMINE,
    GITLAB
}
