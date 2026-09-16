package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

/** Reads the installer password without logging or persisting the raw Secret. */
public final class RootInstallationSecretResolver {
    private static final long MAX_SECRET_BYTES = 4096;

    private RootInstallationSecretResolver() {}

    public static char[] resolve(IamRuntimeProperties properties) {
        String inline = properties.getRootInitialPassword();
        String file = properties.getRootInitialPasswordFile();
        if (inline != null && !inline.isEmpty()) return inline.toCharArray();
        if (file == null || file.isBlank()) return new char[0];

        Path path = Path.of(file).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("AEG_IAM_ROOT_INITIAL_PASSWORD_FILE must reference a regular non-symlink file");
            }
            long size = Files.size(path);
            if (size < 1 || size > MAX_SECRET_BYTES) {
                throw new IllegalStateException("AEG_IAM_ROOT_INITIAL_PASSWORD_FILE must contain between 1 and 4096 bytes");
            }
            byte[] bytes = Files.readAllBytes(path);
            try {
                String value = new String(bytes, StandardCharsets.UTF_8);
                if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
                else if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
                if (value.isEmpty() || value.indexOf('\0') >= 0) {
                    throw new IllegalStateException("AEG_IAM_ROOT_INITIAL_PASSWORD_FILE contains invalid password material");
                }
                return value.toCharArray();
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read AEG_IAM_ROOT_INITIAL_PASSWORD_FILE", ex);
        }
    }
}
