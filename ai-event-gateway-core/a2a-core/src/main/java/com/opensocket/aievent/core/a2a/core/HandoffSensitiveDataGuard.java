package com.opensocket.aievent.core.a2a.core;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Rejects credentials, dispatch tokens and unapproved raw payload containers before persistence. */
public final class HandoffSensitiveDataGuard {
    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "password", "passwd", "secret", "credential", "authorization", "api_key", "apikey",
            "access_token", "refresh_token", "dispatch_token", "fencing_token", "private_key",
            "client_secret", "session_cookie", "raw_payload", "original_payload", "source_payload");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(bearer\\s+[a-z0-9._~+/-]+=*|basic\\s+[a-z0-9+/]+=*|-----begin (rsa |ec |open)?private key-----|(?:api[-_ ]?key|client[-_ ]?secret|password)\\s*[:=])");

    public boolean isForbiddenPath(String path) {
        if (path == null) return false;
        String normalized = path.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        return FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    public boolean containsForbiddenContent(Object value) {
        if (value == null) return false;
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry -> isForbiddenPath(String.valueOf(entry.getKey()))
                    || containsForbiddenContent(entry.getValue()));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::containsForbiddenContent);
        }
        if (value.getClass().isArray()) {
            return java.util.Arrays.deepToString(new Object[] { value }).matches("(?s).*" + SECRET_VALUE.pattern() + ".*");
        }
        return SECRET_VALUE.matcher(String.valueOf(value)).find();
    }

    public void requireSafeReference(String reference) {
        if (reference != null && (isForbiddenPath(reference) || containsForbiddenContent(reference))) {
            throw new IllegalArgumentException("HANDOFF_CONTEXT_FORBIDDEN_SECRET: unsafe reference");
        }
    }
}
