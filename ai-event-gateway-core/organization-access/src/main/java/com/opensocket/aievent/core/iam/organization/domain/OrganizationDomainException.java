package com.opensocket.aievent.core.iam.organization.domain;

public final class OrganizationDomainException extends RuntimeException {
    private final String reasonCode;

    public OrganizationDomainException(String reasonCode, String message) {
        super(message);
        this.reasonCode = OrganizationText.required(reasonCode, "reasonCode", 128);
    }

    public String reasonCode() { return reasonCode; }
}
