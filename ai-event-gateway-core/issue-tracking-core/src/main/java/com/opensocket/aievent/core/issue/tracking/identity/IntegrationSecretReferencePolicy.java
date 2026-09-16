package com.opensocket.aievent.core.issue.tracking.identity;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validates credential references without ever resolving or exposing a secret value. */
public final class IntegrationSecretReferencePolicy {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("vault", "env", "file");
    private IntegrationSecretReferencePolicy() {}

    public static String validate(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("SECRET_REFERENCE_REQUIRED");
        }
        String value = reference.trim();
        if (looksLikeSecretValue(value)) {
            throw new IllegalArgumentException("SECRET_VALUE_MUST_NOT_BE_PERSISTED");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("SECRET_REFERENCE_INVALID", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("SECRET_REFERENCE_SCHEME_NOT_ALLOWED");
        }
        if ((uri.getHost() == null || uri.getHost().isBlank())
                && (uri.getPath() == null || uri.getPath().isBlank())) {
            throw new IllegalArgumentException("SECRET_REFERENCE_TARGET_REQUIRED");
        }
        return value;
    }

    public static boolean productionSafe(String reference) {
        return reference != null && reference.regionMatches(true, 0, "vault://", 0, 8);
    }

    private static boolean looksLikeSecretValue(String value) {
        if (value.contains(" ") || value.contains("\n") || value.contains("\r")) return true;
        if (!value.contains("://") && value.length() >= 20) return true;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("bearer ") || lower.startsWith("basic ") || lower.contains("-----begin private key-----");
    }
}
