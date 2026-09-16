package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Authentication strength and verification time, independent of Spring Security. */
public record AuthenticationAssurance(Level level, Set<String> methods, Instant authenticatedAt) {
    public AuthenticationAssurance {
        Objects.requireNonNull(level, "level");
        methods = methods == null ? Set.of() : Set.copyOf(methods);
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
    }

    public static AuthenticationAssurance passwordOnly(Instant authenticatedAt) {
        return new AuthenticationAssurance(Level.PASSWORD, Set.of("PASSWORD"), authenticatedAt);
    }

    public enum Level {
        ANONYMOUS,
        PASSWORD,
        MFA,
        RECOVERY,
        TOKEN,
        SYSTEM
    }
}
