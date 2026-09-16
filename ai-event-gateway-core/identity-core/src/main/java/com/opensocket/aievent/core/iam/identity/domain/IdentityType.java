package com.opensocket.aievent.core.iam.identity.domain;

import com.opensocket.aievent.core.iam.security.contract.SubjectRef;

/** Canonical identity kinds owned by the identity domain. */
public enum IdentityType {
    INSTANCE_ROOT,
    HUMAN_USER;

    public SubjectRef.IdentityType toContractType() {
        return switch (this) {
            case INSTANCE_ROOT -> SubjectRef.IdentityType.INSTANCE_ROOT;
            case HUMAN_USER -> SubjectRef.IdentityType.HUMAN_USER;
        };
    }
}
