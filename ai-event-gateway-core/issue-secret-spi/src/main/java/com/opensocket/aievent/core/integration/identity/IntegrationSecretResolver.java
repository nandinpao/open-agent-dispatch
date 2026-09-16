package com.opensocket.aievent.core.integration.identity;

public interface IntegrationSecretResolver {
    ResolvedIntegrationSecret resolve(IntegrationCredentialMetadata credential);
    String mode();
    default boolean supports(IntegrationCredentialMetadata credential) {
        return credential != null && credential.secretRef() != null;
    }
}
