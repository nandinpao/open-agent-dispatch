package com.opensocket.aievent.core.agent.governance;

public enum AgentRiskStatus {
    NORMAL,
    QUARANTINED,
    SUSPENDED,
    REVOKED,
    COMPROMISED;

    public boolean allowsAssignment() {
        return this == NORMAL;
    }
}
