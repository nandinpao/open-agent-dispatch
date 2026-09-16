package com.opensocket.aievent.core.iam.identity.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;

public record UserId(String value) {
    public UserId {
        value = DomainText.required(value, "userId", 128);
    }

    public SubjectRef toSubjectRef() {
        return new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, value);
    }

    public PrincipalRef toPrincipalRef() {
        return new PrincipalRef(PrincipalRef.PrincipalType.USER, value);
    }
}
