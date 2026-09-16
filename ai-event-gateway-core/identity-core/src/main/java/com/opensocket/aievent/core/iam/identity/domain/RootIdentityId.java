package com.opensocket.aievent.core.iam.identity.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;

public record RootIdentityId(String value) {
    public static final RootIdentityId INSTANCE = new RootIdentityId("root");

    public RootIdentityId {
        value = DomainText.required(value, "rootIdentityId", 64);
        if (!"root".equals(value)) {
            throw new IllegalArgumentException("instance root identity id must be root");
        }
    }

    public SubjectRef toSubjectRef() {
        return new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, value);
    }

    public PrincipalRef toPrincipalRef() {
        return new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, value);
    }
}
