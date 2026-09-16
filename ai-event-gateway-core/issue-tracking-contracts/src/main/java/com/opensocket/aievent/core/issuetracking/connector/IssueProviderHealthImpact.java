package com.opensocket.aievent.core.issuetracking.connector;

/**
 * How an Issue Provider result affects transport/integration health.
 *
 * <p>HEALTHY means the provider responded and the connection is operational,
 * even if the requested business operation was denied (for example HTTP 403).
 * This intentionally separates provider permission from connection health.</p>
 */
public enum IssueProviderHealthImpact {
    NONE,
    HEALTHY,
    DEGRADED,
    THROTTLED
}
