package com.opensocket.aievent.core.iam.identity.domain;

final class DomainText {
    private DomainText() { }

    static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    static String optional(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
