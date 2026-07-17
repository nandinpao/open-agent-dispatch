package com.opensocket.aievent.core.routing;

/**
 * P3-I controlled rollout mode for the Demand x Policy x Supply eligibility engine.
 */
public enum EligibilityEngineMode {
    LEGACY_ONLY,
    SHADOW,
    WARN,
    ENFORCE;

    public static EligibilityEngineMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SHADOW;
        }
        try {
            return EligibilityEngineMode.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return SHADOW;
        }
    }

    public boolean usesV2() {
        return this != LEGACY_ONLY;
    }

    public boolean warnsOnly() {
        return this == WARN || this == SHADOW;
    }

    public boolean enforce() {
        return this == ENFORCE;
    }
}
