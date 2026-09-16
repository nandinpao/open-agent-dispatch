package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/** Opaque credential/token evidence used to authenticate a machine principal. */
public record MachineCredentialRef(
        String credentialId,
        CredentialType credentialType,
        String tokenId,
        String issuer,
        String clientId
) {
    public MachineCredentialRef {
        credentialId = requireText(credentialId, "credentialId");
        Objects.requireNonNull(credentialType, "credentialType");
        tokenId = normalize(tokenId);
        issuer = normalize(issuer);
        clientId = normalize(clientId);
    }

    public MachineCredentialRef(String credentialId, CredentialType credentialType, String tokenId, String issuer) {
        this(credentialId, credentialType, tokenId, issuer, "");
    }

    /** Transitional projection for the existing opaque service-account access-token model. */
    public static MachineCredentialRef accessToken(String tokenId) {
        String normalized = requireText(tokenId, "tokenId");
        return new MachineCredentialRef("access-token:" + normalized, CredentialType.ACCESS_TOKEN, normalized, "opendispatch", "");
    }

    public enum CredentialType { ACCESS_TOKEN, CLIENT_SECRET, MTLS_CERTIFICATE, AGENT_CREDENTIAL, INTERNAL_SECRET }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
