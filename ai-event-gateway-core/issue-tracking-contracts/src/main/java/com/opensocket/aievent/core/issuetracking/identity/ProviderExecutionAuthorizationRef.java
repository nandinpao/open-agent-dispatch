package com.opensocket.aievent.core.issuetracking.identity;

/**
 * Secret-free binding between a Human authorization decision and the exact Provider
 * principal / credential metadata selected for the ensuing side effect.
 *
 * <p>The downstream Provider adapter must verify that its freshly resolved execution
 * context still matches this reference before reading any secret or issuing a write.
 * A mapping or credential rotation between authorization and execution therefore
 * fails closed instead of silently changing the attributed Provider actor.</p>
 */
public record ProviderExecutionAuthorizationRef(
        String attributionId,
        ProviderWriteIdentityPolicy policy,
        String connectionId,
        String mappingId,
        String integrationPrincipalId,
        String credentialId,
        String credentialVersion,
        String providerActorId) {

    public ProviderExecutionAuthorizationRef {
        attributionId = required(attributionId, "attributionId");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        connectionId = required(connectionId, "connectionId");
        mappingId = required(mappingId, "mappingId");
        integrationPrincipalId = required(integrationPrincipalId, "integrationPrincipalId");
        credentialId = required(credentialId, "credentialId");
        credentialVersion = required(credentialVersion, "credentialVersion");
        providerActorId = providerActorId == null ? "" : providerActorId.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
