package com.opensocket.aievent.core.iam.identity.domain;

/** Root lifecycle is deliberately separate from normal user status. */
public enum RootIdentityStatus {
    BOOTSTRAP_PENDING,
    ACTIVE,
    LOCKED_AFTER_RECOVERY,
    DISABLED
}
