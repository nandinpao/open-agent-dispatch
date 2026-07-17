package com.opensocket.aievent.core.agent.governance;

/**
 * Per-agent duplicate-runtime enforcement strategy.
 * ALERT_ONLY records and notifies only; stronger modes execute quarantine/disconnect/revoke.
 */
public enum AgentSecurityEnforcementMode {
    ALERT_ONLY,
    QUARANTINE,
    QUARANTINE_AND_DISCONNECT,
    QUARANTINE_REVOKE_AND_DISCONNECT
}
