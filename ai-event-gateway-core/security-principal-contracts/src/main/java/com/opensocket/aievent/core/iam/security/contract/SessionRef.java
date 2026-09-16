package com.opensocket.aievent.core.iam.security.contract;

/** Opaque browser or recovery session reference. */
public record SessionRef(String sessionId) {
    public SessionRef {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        sessionId = sessionId.trim();
    }
}
