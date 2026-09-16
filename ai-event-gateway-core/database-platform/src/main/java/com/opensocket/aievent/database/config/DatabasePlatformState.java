package com.opensocket.aievent.database.config;

/**
 * Observable OpenDispatch database lifecycle states.
 */
public enum DatabasePlatformState {
    UNCONFIGURED,
    CONFIGURED,
    CONNECTING,
    READY,
    DEGRADED,
    SHUTTING_DOWN
}
