package com.opensocket.aievent.core.kernel.configuration.revision;

/** Raised when a draft's base revision no longer matches the active revision of its Config Set. */
public final class ConfigurationRevisionConflictException extends RuntimeException {
    public static final String CODE = "CONFIGURATION_REVISION_CONFLICT";

    public ConfigurationRevisionConflictException(String message) {
        super(CODE + ": " + message);
    }
}
