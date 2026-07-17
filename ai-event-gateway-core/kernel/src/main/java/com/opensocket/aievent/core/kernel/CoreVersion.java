package com.opensocket.aievent.core.kernel;

/**
 * Runtime version exposed by the Core status API.
 *
 * <p>M1 keeps this value in the framework-neutral kernel so the executable
 * application and future feature modules use one source of truth.</p>
 */
public final class CoreVersion {
    public static final String CURRENT = "1.0.0-p25.7.4-p5-callback-transition-governance-fix";

    private CoreVersion() {
    }
}
