package com.opensocket.aievent.core.issue.secret.vault;

import com.opensocket.aievent.core.integration.identity.IntegrationCredentialMetadata;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.ResolvedIntegrationSecret;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** HashiCorp Vault KV-v2 resolver. Secret values exist only in an erasable char array. */
public final class VaultIntegrationSecretResolver implements IntegrationSecretResolver {
    private final URI address;
    private final String namespace;
    private final String tokenReference;
    private final ObjectMapper json;
    private final HttpClient http;

    public VaultIntegrationSecretResolver(String address, String namespace, String tokenReference, ObjectMapper json) {
        if (address == null || address.isBlank()) throw new IllegalStateException("VAULT_ADDRESS_REQUIRED");
        if (tokenReference == null || tokenReference.isBlank()) throw new IllegalStateException("VAULT_TOKEN_REFERENCE_REQUIRED");
        this.address = URI.create(address.endsWith("/") ? address.substring(0, address.length() - 1) : address);
        this.namespace = namespace == null ? "" : namespace.trim();
        this.tokenReference = tokenReference.trim();
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public ResolvedIntegrationSecret resolve(IntegrationCredentialMetadata credential) {
        if (!supports(credential)) throw new IllegalStateException("VAULT_SECRET_REFERENCE_REQUIRED");
        VaultReference reference = VaultReference.parse(credential.secretRef(), credential.secretVersion());
        char[] token = bootstrapToken();
        try {
            String endpoint = address + "/v1/" + reference.mount() + "/data/" + reference.path()
                    + (reference.version() == null ? "" : "?version=" + reference.version());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15)).GET()
                    .header("X-Vault-Token", new String(token));
            if (!namespace.isBlank()) request.header("X-Vault-Namespace", namespace);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("VAULT_SECRET_READ_FAILED_STATUS_" + response.statusCode());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = json.readValue(response.body(), Map.class);
            Object dataNode = root.get("data");
            if (!(dataNode instanceof Map<?, ?> envelope) || !(envelope.get("data") instanceof Map<?, ?> data)) {
                throw new IllegalStateException("VAULT_SECRET_RESPONSE_INVALID");
            }
            Object value = data.get(reference.field());
            if (value == null || String.valueOf(value).isBlank()) throw new IllegalStateException("VAULT_SECRET_FIELD_MISSING");
            return new ResolvedIntegrationSecret(String.valueOf(value).toCharArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("VAULT_SECRET_READ_INTERRUPTED", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("VAULT_SECRET_READ_FAILED", ex);
        } finally {
            java.util.Arrays.fill(token, '\0');
        }
    }

    @Override public String mode() { return "VAULT"; }
    @Override public boolean supports(IntegrationCredentialMetadata credential) {
        return credential != null && credential.secretRef() != null && credential.secretRef().startsWith("vault://");
    }

    private char[] bootstrapToken() {
        String value;
        if (tokenReference.startsWith("env://")) {
            value = System.getenv(tokenReference.substring(6));
        } else if (tokenReference.startsWith("file://")) {
            try { value = Files.readString(Path.of(tokenReference.substring(7))).trim(); }
            catch (Exception ex) { throw new IllegalStateException("VAULT_TOKEN_REFERENCE_UNRESOLVABLE", ex); }
        } else {
            throw new IllegalStateException("VAULT_TOKEN_REFERENCE_SCHEME_NOT_ALLOWED");
        }
        if (value == null || value.isBlank()) throw new IllegalStateException("VAULT_TOKEN_REFERENCE_EMPTY");
        return value.toCharArray();
    }

    private record VaultReference(String mount, String path, String field, String version) {
        static VaultReference parse(String source, String configuredVersion) {
            String raw = source.substring("vault://".length());
            int fragment = raw.indexOf('#');
            String field = fragment >= 0 ? raw.substring(fragment + 1) : "value";
            String location = fragment >= 0 ? raw.substring(0, fragment) : raw;
            int slash = location.indexOf('/');
            if (slash <= 0 || slash == location.length() - 1) throw new IllegalStateException("VAULT_SECRET_REFERENCE_INVALID");
            return new VaultReference(location.substring(0, slash), location.substring(slash + 1), field,
                    configuredVersion == null || configuredVersion.isBlank() ? null : configuredVersion.trim());
        }
    }
}
