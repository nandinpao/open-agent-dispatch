package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stable permission identifier.
 *
 * <p>The PostgreSQL permission authority stores canonical codes in a 160-character column.
 * Published OpenDispatch catalogs contain compact permissions such as {@code task.read} and
 * generated administration permissions with up to eleven lower-case dot-separated segments.
 * The value object therefore validates the database authority contract instead of imposing an
 * incompatible six-segment limit.</p>
 */
public record PermissionCode(String value) {
    public static final int MAX_LENGTH = 160;

    private static final Pattern PATTERN = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

    public PermissionCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("permission code is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "permission code must contain at least two lower-case dot-separated segments and be at most "
                            + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
