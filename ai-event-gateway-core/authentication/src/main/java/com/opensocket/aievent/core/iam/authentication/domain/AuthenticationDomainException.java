package com.opensocket.aievent.core.iam.authentication.domain;

public final class AuthenticationDomainException extends RuntimeException {
    private final AuthenticationReasonCode reasonCode;
    public AuthenticationDomainException(AuthenticationReasonCode reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
    public AuthenticationReasonCode reasonCode() { return reasonCode; }
}
