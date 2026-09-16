package com.opensocket.aievent.core.iam.runtime.security;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local/on-prem one-time-secret delivery sink.
 *
 * <p>The directory is restricted to the runtime account. R2 adds a relative browser action path so
 * operators testing the secure-file adapter can open the same guided setup flow a mail adapter would
 * render, rather than manually copying an opaque token into an administration form.</p>
 */
public final class SecureFileOneTimeSecretDeliveryAdapter implements IamOneTimeSecretDeliveryPort {
    private final Path directory;
    private final ObjectMapper mapper;
    private final java.time.Clock clock;

    public SecureFileOneTimeSecretDeliveryAdapter(String directory, ObjectMapper mapper, java.time.Clock clock) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.mapper = mapper;
        this.clock = clock;
        initialize();
    }

    @Override
    public void deliver(String purpose, String recipient, String secret, Instant expires, String correlation) {
        try {
            String name = clock.instant().toEpochMilli() + "-" + UUID.randomUUID() + ".json";
            Path temp = Files.createTempFile(directory, ".pending-", ".tmp");
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("purpose", purpose);
            envelope.put("recipientReference", recipient);
            envelope.put("secret", secret);
            envelope.put("actionPath", actionPath(purpose, secret));
            envelope.put("expiresAt", expires.toString());
            envelope.put("correlationId", correlation);
            Files.writeString(temp, mapper.writeValueAsString(envelope), StandardOpenOption.TRUNCATE_EXISTING);
            set600(temp);
            Files.move(temp, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            throw new IllegalStateException("IAM_ONE_TIME_SECRET_DELIVERY_FAILED", ex);
        }
    }

    private String actionPath(String purpose, String secret) {
        String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);
        if ("USER_INVITATION".equals(purpose)) return "/activate-account?token=" + encoded;
        if ("ACCOUNT_SETUP".equals(purpose)
                || "ADMIN_PASSWORD_RESET".equals(purpose)
                || "PASSWORD_RESET".equals(purpose)
                || "ROOT_ADMIN_PASSWORD_RESET".equals(purpose)
                || "TENANT_ADMIN_INITIAL_PASSWORD".equals(purpose)) {
            return "/reset-password?token=" + encoded;
        }
        return "";
    }

    private void initialize() {
        try {
            Files.createDirectories(directory);
            try {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystems rely on the container/host ACL.
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize IAM one-time secret delivery directory", ex);
        }
    }

    private void set600(Path path) throws Exception {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on the container/host ACL.
        }
    }
}
