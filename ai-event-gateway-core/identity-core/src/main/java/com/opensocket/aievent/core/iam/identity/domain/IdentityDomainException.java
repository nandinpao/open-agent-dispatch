package com.opensocket.aievent.core.iam.identity.domain;

/** Stable domain failure with a machine-readable IAM reason code. */
public final class IdentityDomainException extends RuntimeException {
    private final String reasonCode;

    public IdentityDomainException(String reasonCode, String message) {
        super(message);
        this.reasonCode = DomainText.required(reasonCode, "reasonCode", 128);
    }

    public String reasonCode() {
        return reasonCode;
    }
}
