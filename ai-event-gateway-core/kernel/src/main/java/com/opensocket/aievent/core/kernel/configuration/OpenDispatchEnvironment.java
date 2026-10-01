package com.opensocket.aievent.core.kernel.configuration;

import java.util.Locale;
import java.util.Map;

/** Canonical immutable OpenDispatch deployment environment identity. */
public enum OpenDispatchEnvironment {
    PRD, UAT, SIT, QA, DEV, LOCAL;

    private static final Map<String, OpenDispatchEnvironment> SPRING_PROFILE_COMPATIBILITY = Map.of(
            "prod", PRD,
            "uat", UAT,
            "sit", SIT,
            "qa", QA,
            "dev", DEV,
            "local", LOCAL
    );

    public static OpenDispatchEnvironment parseCanonical(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OPENDISPATCH_ENVIRONMENT is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static OpenDispatchEnvironment fromEnvironmentSpringProfile(String profile) {
        if (profile == null) {
            return null;
        }
        return SPRING_PROFILE_COMPATIBILITY.get(profile.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isEnvironmentSpringProfile(String profile) {
        return fromEnvironmentSpringProfile(profile) != null;
    }
}
