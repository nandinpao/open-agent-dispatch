package com.opensocket.aievent.core.issuetracking.connector;

/**
 * Whether OpenDispatch knows the final external side-effect outcome.
 *
 * <p>UNCERTAIN is used when a mutating Redmine request may have been accepted but
 * the response was lost or the provider returned an ambiguous server failure.
 * OpenDispatch must not blindly auto-retry such a write because Redmine does not
 * provide a native exactly-once idempotency contract for these operations.</p>
 */
public enum IssueProviderOutcomeCertainty {
    CONFIRMED,
    UNCERTAIN
}
