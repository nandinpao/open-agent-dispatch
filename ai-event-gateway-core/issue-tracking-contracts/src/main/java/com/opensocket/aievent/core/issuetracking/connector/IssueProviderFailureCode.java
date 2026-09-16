package com.opensocket.aievent.core.issuetracking.connector;

/**
 * Stable OpenDispatch semantics for Issue Provider execution failures.
 *
 * <p>These codes describe the provider result; they are not OpenDispatch Issue
 * permissions. Redmine remains the authority for project, role, workflow and
 * operation authorization.</p>
 */
public enum IssueProviderFailureCode {
    ISSUE_PROVIDER_CONFIGURATION_INVALID,
    ISSUE_PROVIDER_AUTHENTICATION_FAILED,
    ISSUE_PROVIDER_PERMISSION_DENIED,
    ISSUE_PROVIDER_RESOURCE_NOT_FOUND,
    ISSUE_PROVIDER_CONFLICT,
    ISSUE_PROVIDER_VALIDATION_FAILED,
    ISSUE_PROVIDER_RATE_LIMITED,
    ISSUE_PROVIDER_TIMEOUT,
    ISSUE_PROVIDER_OUTCOME_UNCERTAIN,
    ISSUE_PROVIDER_UNAVAILABLE,
    ISSUE_PROVIDER_EXECUTION_FAILED
}
