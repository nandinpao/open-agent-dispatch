package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;

public final class RbacDomainException extends RuntimeException {
    private final RbacReasonCode reasonCode;
    public RbacDomainException(RbacReasonCode reasonCode, String message) {
        super(message);
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }
    public RbacReasonCode reasonCode() { return reasonCode; }
}
