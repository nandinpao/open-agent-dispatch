package com.opensocket.aievent.database.health;

import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.opensocket.aievent.database.config.DatabasePlatformProperties;
import com.opensocket.aievent.database.config.DatabasePlatformRuntimeInspector;

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
        if (!Boolean.TRUE.equals(details.get("dataSourceAvailable"))) {
            return Health.unknown().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
