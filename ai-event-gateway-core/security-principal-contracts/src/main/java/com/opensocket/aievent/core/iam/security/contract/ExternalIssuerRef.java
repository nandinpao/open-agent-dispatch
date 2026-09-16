package com.opensocket.aievent.core.iam.security.contract;

/** External OAuth2/OIDC issuer and subject binding reference. */
public record ExternalIssuerRef(String issuer, String subject) {
    public ExternalIssuerRef {
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("issuer is required");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        issuer = issuer.trim();
        subject = subject.trim();
    }
}
