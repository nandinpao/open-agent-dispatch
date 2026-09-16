package com.opensocket.aievent.database.health;

import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import com.opensocket.aievent.database.config.DatabasePlatformLifecycleMode;
import com.opensocket.aievent.database.config.DatabasePlatformProperties;
import com.opensocket.aievent.database.config.DatabasePlatformRuntimeInspector;
import com.opensocket.aievent.database.config.DatabasePlatformState;

/**
 * Readiness-oriented database health. Liveness must not depend on database reachability.
 */
public class DatabasePlatformHealthIndicator implements HealthIndicator {
    private final DatabasePlatformRuntimeInspector inspector;
    private final DatabasePlatformProperties properties;

    public DatabasePlatformHealthIndicator(DatabasePlatformRuntimeInspector inspector,
                                           DatabasePlatformProperties properties) {
        this.inspector = inspector;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = inspector.snapshot();
        DatabasePlatformState state = lifecycleState(details);

        if (state == DatabasePlatformState.UNCONFIGURED) {
            if (properties.getLifecycleMode() == DatabasePlatformLifecycleMode.SETUP_TOLERANT) {
                return Health.status(Status.OUT_OF_SERVICE).withDetails(details).build();
            }
            return Health.down().withDetails(details).build();
        }
        if (state == DatabasePlatformState.CONFIGURED || state == DatabasePlatformState.CONNECTING) {
            return Health.status(Status.OUT_OF_SERVICE).withDetails(details).build();
        }
        if (state == DatabasePlatformState.DEGRADED || state == DatabasePlatformState.SHUTTING_DOWN) {
            return Health.down().withDetails(details).build();
        }
        if (properties.isRequireDataSource() && !Boolean.TRUE.equals(details.get("dataSourceAvailable"))) {
            return Health.down().withDetails(details).build();
        }
        if (properties.isRequireSqlSessionFactory()
                && !Boolean.TRUE.equals(details.get("sqlSessionFactoryAvailable"))) {
            return Health.down().withDetails(details).build();
        }
        if (properties.isRequireFlyway() && !Boolean.TRUE.equals(details.get("flywayAvailable"))) {
            return Health.down().withDetails(details).build();
        }
        if (Boolean.FALSE.equals(details.get("connectionValid")) || details.containsKey("flywayError")) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }

    private static DatabasePlatformState lifecycleState(Map<String, Object> details) {
        Object raw = details.get("state");
        if (raw == null) {
            return DatabasePlatformState.DEGRADED;
        }
        try {
            return DatabasePlatformState.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            return DatabasePlatformState.DEGRADED;
        }
    }
}
