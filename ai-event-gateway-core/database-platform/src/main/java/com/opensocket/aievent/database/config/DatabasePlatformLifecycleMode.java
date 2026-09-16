package com.opensocket.aievent.database.config;

/**
 * Runtime policy for database availability during application bootstrap.
 */
public enum DatabasePlatformLifecycleMode {
    /**
     * Local/setup policy. An explicitly disabled or incomplete database configuration is exposed as
     * UNCONFIGURED instead of being treated as a production-ready database.
     */
    SETUP_TOLERANT,

    /**
     * Release/production policy. Database infrastructure is mandatory and any unavailable state is
     * fail-closed for readiness.
     */
    REQUIRED
}
