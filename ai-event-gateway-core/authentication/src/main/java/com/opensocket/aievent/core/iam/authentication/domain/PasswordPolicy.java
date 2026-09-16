package com.opensocket.aievent.core.iam.authentication.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PasswordPolicy(
        int minimumLength,
        int maximumLength,
        boolean requireUppercase,
        boolean requireLowercase,
        boolean requireNumber,
        boolean requireSymbol,
        int passwordHistoryCount,
        Duration maximumAge,
        Duration minimumAge,
        int failedAttemptThreshold,
        Duration lockoutDuration,
        BreachedPasswordMode breachedPasswordMode) {

    public PasswordPolicy {
        if (minimumLength < 12 || minimumLength > 256) {
            throw new IllegalArgumentException("minimumLength must be 12..256");
        }
        if (maximumLength < minimumLength || maximumLength > 1024) {
            throw new IllegalArgumentException("maximumLength is invalid");
        }
        if (passwordHistoryCount < 0 || passwordHistoryCount > 50) {
            throw new IllegalArgumentException("passwordHistoryCount must be 0..50");
        }
        maximumAge = maximumAge == null ? Duration.ofDays(90) : maximumAge;
        minimumAge = minimumAge == null ? Duration.ZERO : minimumAge;
        if (maximumAge.isNegative() || maximumAge.isZero()) {
            throw new IllegalArgumentException("maximumAge must be positive");
        }
        if (minimumAge.isNegative() || minimumAge.compareTo(maximumAge) >= 0) {
            throw new IllegalArgumentException("minimumAge is invalid");
        }
        if (failedAttemptThreshold < 3 || failedAttemptThreshold > 50) {
            throw new IllegalArgumentException("failedAttemptThreshold must be 3..50");
        }
        lockoutDuration = lockoutDuration == null ? Duration.ofMinutes(15) : lockoutDuration;
        if (lockoutDuration.isNegative() || lockoutDuration.isZero()) {
            throw new IllegalArgumentException("lockoutDuration must be positive");
        }
        breachedPasswordMode = breachedPasswordMode == null
                ? BreachedPasswordMode.AUDIT : breachedPasswordMode;
    }

    public static PasswordPolicy secureDefault() {
        return new PasswordPolicy(14, 256, true, true, true, true, 10,
                Duration.ofDays(90), Duration.ofHours(1), 5,
                Duration.ofMinutes(15), BreachedPasswordMode.AUDIT);
    }

    /** Validates directly against the caller-owned character array without creating a password String. */
    public List<String> violations(char[] password, String username) {
        if (password == null) return List.of("PASSWORD_REQUIRED");
        List<String> result = new ArrayList<>();
        if (password.length < minimumLength) result.add("PASSWORD_TOO_SHORT");
        if (password.length > maximumLength) result.add("PASSWORD_TOO_LONG");

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasNumber = false;
        boolean hasSymbol = false;
        for (char value : password) {
            hasUppercase |= Character.isUpperCase(value);
            hasLowercase |= Character.isLowerCase(value);
            hasNumber |= Character.isDigit(value);
            hasSymbol |= !Character.isLetterOrDigit(value);
        }
        if (requireUppercase && !hasUppercase) result.add("PASSWORD_UPPERCASE_REQUIRED");
        if (requireLowercase && !hasLowercase) result.add("PASSWORD_LOWERCASE_REQUIRED");
        if (requireNumber && !hasNumber) result.add("PASSWORD_NUMBER_REQUIRED");
        if (requireSymbol && !hasSymbol) result.add("PASSWORD_SYMBOL_REQUIRED");
        if (containsIgnoreCase(password, username)) result.add("PASSWORD_CONTAINS_USERNAME");
        return List.copyOf(result);
    }

    private boolean containsIgnoreCase(char[] password, String username) {
        if (username == null || username.isBlank()) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > password.length) return false;
        for (int start = 0; start <= password.length - normalized.length(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < normalized.length(); offset++) {
                if (Character.toLowerCase(password[start + offset]) != normalized.charAt(offset)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    public enum BreachedPasswordMode { DISABLED, AUDIT, BLOCK }
}
