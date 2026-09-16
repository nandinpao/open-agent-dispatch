package com.opensocket.aievent.core.integration.identity;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Verifies that credential metadata points to secret material that the active
 * runtime resolver can actually open.
 *
 * <p>This is intentionally stronger than checking {@link IntegrationCredentialStatus}.
 * A credential row can remain ACTIVE after a mounted file, environment variable,
 * Vault path, or resolver configuration has disappeared. Runtime readiness must
 * therefore include material resolution, while never returning or logging the
 * secret value or the full secret reference.</p>
 */
public final class IntegrationCredentialMaterialVerifier {
    private final ObjectProvider<IntegrationSecretResolver> resolvers;

    public IntegrationCredentialMaterialVerifier(ObjectProvider<IntegrationSecretResolver> resolvers) {
        this.resolvers = resolvers;
    }

    public CredentialMaterialReadiness verify(IntegrationCredentialMetadata credential) {
        if (credential == null) {
            return CredentialMaterialReadiness.notReady(null, null, "ISSUE_CREDENTIAL_METADATA_REQUIRED");
        }
        List<IntegrationSecretResolver> installed = resolvers.orderedStream().toList();
        if (installed.isEmpty()) {
            return CredentialMaterialReadiness.notReady(secretScheme(credential.secretRef()), null,
                    "ISSUE_CREDENTIAL_SECRET_RESOLVER_UNAVAILABLE");
        }
        if (installed.size() != 1) {
            return CredentialMaterialReadiness.notReady(secretScheme(credential.secretRef()), null,
                    "ISSUE_CREDENTIAL_SECRET_RESOLVER_AMBIGUOUS");
        }
        IntegrationSecretResolver resolver = installed.getFirst();
        if (!resolver.supports(credential)) {
            return CredentialMaterialReadiness.notReady(secretScheme(credential.secretRef()), resolver.mode(),
                    "ISSUE_CREDENTIAL_SECRET_REFERENCE_UNSUPPORTED");
        }
        try (ResolvedIntegrationSecret ignored = resolver.resolve(credential)) {
            return CredentialMaterialReadiness.ready(secretScheme(credential.secretRef()), resolver.mode());
        } catch (RuntimeException ex) {
            return CredentialMaterialReadiness.notReady(secretScheme(credential.secretRef()), resolver.mode(), classify(ex));
        }
    }

    public CredentialMaterialReadiness requireReady(IntegrationCredentialMetadata credential) {
        CredentialMaterialReadiness readiness = verify(credential);
        if (!readiness.materialResolvable()) {
            throw new IllegalStateException(readiness.errorCode());
        }
        return readiness;
    }

    private static String classify(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null || message.isBlank()) return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
        if (message.contains("SECRET_REFERENCE_REQUIRED")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_REQUIRED";
        if (message.contains("SECRET_REFERENCE_EMPTY")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_EMPTY";
        if (message.contains("SECRET_REFERENCE_SCHEME_NOT_SUPPORTED")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_UNSUPPORTED";
        if (message.contains("SECRET_REFERENCE_UNRESOLVABLE")) return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
        if (message.contains("VAULT_")) return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
        return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
    }

    private static String secretScheme(String reference) {
        if (reference == null || reference.isBlank()) return "MISSING";
        int marker = reference.indexOf("://");
        if (marker <= 0) return "UNKNOWN";
        return reference.substring(0, marker).trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record CredentialMaterialReadiness(
            boolean materialResolvable,
            String secretScheme,
            String resolverMode,
            String errorCode) {
        static CredentialMaterialReadiness ready(String scheme, String resolverMode) {
            return new CredentialMaterialReadiness(true, scheme, resolverMode, null);
        }
        static CredentialMaterialReadiness notReady(String scheme, String resolverMode, String errorCode) {
            return new CredentialMaterialReadiness(false, scheme, resolverMode, errorCode);
        }
    }
}
