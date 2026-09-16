package com.opensocket.aievent.core.iam.token.domain;

/** Lifecycle of an instance-level JWT signing key. */
public enum MachineSigningKeyStatus {
    ACTIVE,
    VERIFY_ONLY,
    RETIRED
}
