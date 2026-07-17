package com.opensocket.aievent.core.agent.governance;

public enum AgentApprovalStatus {
    REGISTERED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED,
    REVOKED;

    public boolean isApproved() {
        return this == APPROVED;
    }
}
