package com.opensocket.aievent.core.issue.secret.env;

import com.opensocket.aievent.core.integration.identity.IntegrationCredentialMetadata;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.ResolvedIntegrationSecret;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;

/** Local/CI resolver. It is deliberately unavailable under the prod profile. */
@Profile("!prod")
public final class EnvironmentIntegrationSecretResolver implements IntegrationSecretResolver {
    @Override
    public ResolvedIntegrationSecret resolve(IntegrationCredentialMetadata credential) {
        String ref = credential == null ? null : credential.secretRef();
        if (ref == null || ref.isBlank()) throw new IllegalStateException("SECRET_REFERENCE_REQUIRED");
        String value;
        if (ref.startsWith("env://")) {
            value = System.getenv(ref.substring(6));
        } else if (ref.startsWith("file://")) {
            try {
                value = Files.readString(Path.of(ref.substring(7))).trim();
            } catch (Exception ex) {
                throw new IllegalStateException("SECRET_REFERENCE_UNRESOLVABLE", ex);
            }
        } else {
            throw new IllegalStateException("SECRET_REFERENCE_SCHEME_NOT_SUPPORTED_BY_ENV_RESOLVER");
        }
        if (value == null || value.isBlank()) throw new IllegalStateException("SECRET_REFERENCE_EMPTY");
        return new ResolvedIntegrationSecret(value.toCharArray());
    }

    @Override public String mode() { return "ENV_REFERENCE"; }
    @Override public boolean supports(IntegrationCredentialMetadata credential) {
        String ref = credential == null ? null : credential.secretRef();
        return ref != null && (ref.startsWith("env://") || ref.startsWith("file://"));
    }
}
