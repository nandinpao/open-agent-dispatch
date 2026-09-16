package com.opensocket.aievent.core.integration.issue.credential;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * One-way secret intake for Integration credentials.
 * Raw provider secrets are accepted only for the duration of this call and are
 * written to the configured secret authority. The database continues to store
 * only file:// or vault:// references.
 */
@Service
public final class ManagedIntegrationSecretIntakeService {
    private static final Set<PosixFilePermission> DIR_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Environment environment;
    private final ObjectMapper json;
    private final HttpClient http;

    public ManagedIntegrationSecretIntakeService(Environment environment, ObjectMapper json) {
        this.environment = environment;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public StoredSecret storeRedmineApiKey(String tenantId, String principalId, String credentialId, char[] apiKey) {
        if (apiKey == null || apiKey.length < 8) throw new IllegalArgumentException("REDMINE_API_KEY_REQUIRED");
        String resolver = environment.getProperty("integration-identity.secret-resolver", "ENV_REFERENCE").trim().toUpperCase();
        return switch (resolver) {
            case "ENV_REFERENCE" -> storeLocalFile(tenantId, principalId, credentialId, apiKey);
            case "VAULT" -> storeVault(tenantId, principalId, credentialId, apiKey);
            default -> throw new IllegalStateException("MANAGED_SECRET_INTAKE_UNAVAILABLE_FOR_RESOLVER_" + resolver);
        };
    }

    private StoredSecret storeLocalFile(String tenantId, String principalId, String credentialId, char[] apiKey) {
        String root = environment.getProperty("integration-identity.managed-secret-directory",
                "/var/lib/opendispatch/integration-secrets");
        Path directory = Path.of(root, safe(tenantId), safe(principalId)).toAbsolutePath().normalize();
        Path target = directory.resolve(safe(credentialId) + ".secret").normalize();
        if (!target.startsWith(directory)) throw new IllegalStateException("MANAGED_SECRET_PATH_ESCAPE_REJECTED");
        Path temp = directory.resolve("." + safe(credentialId) + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(directory);
            setPermissions(directory, DIR_PERMISSIONS);
            byte[] bytes = new String(apiKey).getBytes(StandardCharsets.UTF_8);
            try {
                Files.write(temp, bytes);
                setPermissions(temp, FILE_PERMISSIONS);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                setPermissions(target, FILE_PERMISSIONS);
            } finally {
                Arrays.fill(bytes, (byte) 0);
                Files.deleteIfExists(temp);
            }
            return new StoredSecret("file://" + target, version(), last4(apiKey), "LOCAL_MANAGED_FILE");
        } catch (Exception ex) {
            throw new IllegalStateException("MANAGED_SECRET_FILE_WRITE_FAILED", ex);
        }
    }

    private StoredSecret storeVault(String tenantId, String principalId, String credentialId, char[] apiKey) {
        String address = requiredProperty("integration-identity.vault.address", "INTEGRATION_VAULT_ADDRESS_REQUIRED");
        String tokenRef = requiredProperty("integration-identity.vault.token-ref", "INTEGRATION_VAULT_TOKEN_REF_REQUIRED");
        String namespace = environment.getProperty("integration-identity.vault.namespace", "").trim();
        String mount = environment.getProperty("integration-identity.vault.managed-mount", "secret").trim();
        String prefix = environment.getProperty("integration-identity.vault.managed-prefix", "opendispatch/integrations").trim();
        String path = safePath(prefix) + "/" + safe(tenantId) + "/" + safe(principalId) + "/" + safe(credentialId);
        char[] vaultToken = resolveBootstrapSecret(tokenRef);
        try {
            String endpoint = stripSlash(address) + "/v1/" + safePath(mount) + "/data/" + path;
            String body = json.writeValueAsString(Map.of("data", Map.of("apiKey", new String(apiKey))));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", new String(vaultToken))
                    .PUT(HttpRequest.BodyPublishers.ofString(body));
            if (!namespace.isBlank()) builder.header("X-Vault-Namespace", namespace);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MANAGED_SECRET_VAULT_WRITE_FAILED_STATUS_" + response.statusCode());
            }
            return new StoredSecret("vault://" + safePath(mount) + "/" + path + "#apiKey",
                    version(), last4(apiKey), "VAULT");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MANAGED_SECRET_VAULT_WRITE_INTERRUPTED", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("MANAGED_SECRET_VAULT_WRITE_FAILED", ex);
        } finally {
            Arrays.fill(vaultToken, '\0');
        }
    }

    private char[] resolveBootstrapSecret(String reference) {
        try {
            String value;
            if (reference.startsWith("env://")) value = System.getenv(reference.substring(6));
            else if (reference.startsWith("file://")) value = Files.readString(Path.of(reference.substring(7))).trim();
            else throw new IllegalStateException("VAULT_TOKEN_REFERENCE_SCHEME_NOT_ALLOWED");
            if (value == null || value.isBlank()) throw new IllegalStateException("VAULT_TOKEN_REFERENCE_EMPTY");
            return value.toCharArray();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("VAULT_TOKEN_REFERENCE_UNRESOLVABLE", ex);
        }
    }

    private String requiredProperty(String property, String error) {
        String value = environment.getProperty(property, "").trim();
        if (value.isBlank()) throw new IllegalStateException(error);
        return value;
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try { Files.setPosixFilePermissions(path, permissions); }
        catch (UnsupportedOperationException ignored) { /* Non-POSIX development filesystem. */ }
        catch (Exception ex) { throw new IllegalStateException("MANAGED_SECRET_PERMISSION_HARDENING_FAILED", ex); }
    }

    private static String safe(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        if (normalized.isBlank()) throw new IllegalArgumentException("MANAGED_SECRET_IDENTIFIER_REQUIRED");
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private static String safePath(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank() || normalized.contains("..")) throw new IllegalArgumentException("MANAGED_SECRET_VAULT_PATH_INVALID");
        return normalized;
    }

    private static String stripSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private static String version() { return "managed-" + UUID.randomUUID(); }
    private static String last4(char[] value) { return new String(value, Math.max(0, value.length - 4), Math.min(4, value.length)); }

    public record StoredSecret(String secretRef, String secretVersion, String secretLast4, String storageMode) {}
}
