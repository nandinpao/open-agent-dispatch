package com.opensocket.aievent.core.iam.identity.domain;

/** Human identity lifecycle status. DELETED is terminal. */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    LOCKED,
    SUSPENDED,
    DISABLED,
    PASSWORD_RESET_REQUIRED,
    MFA_ENROLLMENT_REQUIRED,
    DELETED
}
