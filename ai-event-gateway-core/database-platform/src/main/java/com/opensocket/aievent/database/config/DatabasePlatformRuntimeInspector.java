package com.opensocket.aievent.database.config;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

/**
 * Observes the SharedUtility-owned database runtime without creating a second DataSource lifecycle.
 *
 * <p>The inspector intentionally has no scheduler. Actuator/readiness callers trigger a bounded probe,
 * and the result is cached for a configured interval so an unavailable database does not create a
 * connection/log storm. State transitions are logged once instead of logging every failed health poll.</p>
 */
public class DatabasePlatformRuntimeInspector implements SmartInitializingSingleton, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(DatabasePlatformRuntimeInspector.class);

    private final ObjectProvider<DataSource> dataSources;
    private final ObjectProvider<SqlSessionFactory> sqlSessionFactories;
    private final ObjectProvider<Flyway> flyways;
    private final DatabasePlatformProperties properties;
    private final Environment environment;
    private final Object probeMonitor = new Object();

    private volatile DatabasePlatformState state = DatabasePlatformState.UNCONFIGURED;
    private volatile ProbeResult lastProbe;
    private volatile Instant nextProbeAt = Instant.EPOCH;
    private volatile Instant lastTransitionAt = Instant.now();
    private volatile long transitionCount;

    public DatabasePlatformRuntimeInspector(ObjectProvider<DataSource> dataSources,
                                            ObjectProvider<SqlSessionFactory> sqlSessionFactories,
                                            ObjectProvider<Flyway> flyways,
                                            DatabasePlatformProperties properties,
                                            Environment environment) {
        this.dataSources = dataSources;
        this.sqlSessionFactories = sqlSessionFactories;
        this.flyways = flyways;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!databaseConfigured()) {
            transition(DatabasePlatformState.UNCONFIGURED, "DATABASE_CONFIGURATION_DISABLED_OR_INCOMPLETE");
        } else {
            transition(DatabasePlatformState.CONFIGURED, "DATABASE_CONFIGURATION_PRESENT");
        }
        if (properties.isValidateOnStartup()) {
            validateRequiredInfrastructure();
        }
    }

    @Override
    public void destroy() {
        transition(DatabasePlatformState.SHUTTING_DOWN, "APPLICATION_CONTEXT_SHUTDOWN");
    }

    public DatabasePlatformState state() {
        return state;
    }

    public void validateRequiredInfrastructure() {
        boolean configured = databaseConfigured();
        if (!configured && properties.getLifecycleMode() == DatabasePlatformLifecycleMode.SETUP_TOLERANT) {
            return;
        }
        if (!configured) {
            throw new IllegalStateException(
                    "Database platform is REQUIRED but PostgreSQL is disabled or incomplete; configure pg.single.* before startup");
        }
        if (properties.isRequireDataSource() && first(dataSources) == null) {
            throw new IllegalStateException("Database platform requires a DataSource bean");
        }
        if (properties.isRequireSqlSessionFactory() && first(sqlSessionFactories) == null) {
            throw new IllegalStateException("Database platform requires a MyBatis SqlSessionFactory bean");
        }
        if (properties.isRequireFlyway() && first(flyways) == null) {
            throw new IllegalStateException("Database platform requires a Flyway bean");
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean configured = databaseConfigured();
        details.put("lifecycleMode", properties.getLifecycleMode().name());
        details.put("databaseConfigured", configured);
        details.put("pgEnabled", pgEnabled());

        DataSource dataSource = first(dataSources);
        SqlSessionFactory factory = first(sqlSessionFactories);
        Flyway flyway = first(flyways);
        details.put("dataSourceAvailable", dataSource != null);
        details.put("sqlSessionFactoryAvailable", factory != null);
        details.put("flywayAvailable", flyway != null);

        if (!configured) {
            transition(DatabasePlatformState.UNCONFIGURED, "DATABASE_CONFIGURATION_DISABLED_OR_INCOMPLETE");
            details.put("reasonCode", "DATABASE_UNCONFIGURED");
            details.put("recommendedAction", "Configure PostgreSQL and enable PG_ENABLED before database-backed operations");
            appendLifecycle(details);
            return details;
        }

        if (dataSource == null) {
            transition(DatabasePlatformState.DEGRADED, "DATASOURCE_BEAN_UNAVAILABLE");
            details.put("reasonCode", "DATABASE_DATASOURCE_UNAVAILABLE");
            details.put("recommendedAction", "Verify SharedUtility pg.single configuration and DataSource auto-configuration");
            appendLifecycle(details);
            return details;
        }

        ProbeResult probe = connectionProbe(dataSource);
        details.put("connectionValid", probe.valid());
        details.put("connectionProbeCached", probe.cached());
        details.put("connectionProbeAt", probe.probedAt().toString());
        if (probe.databaseProduct() != null) {
            details.put("databaseProduct", probe.databaseProduct());
        }
        if (probe.databaseVersion() != null) {
            details.put("databaseVersion", probe.databaseVersion());
        }
        if (probe.error() != null) {
            details.put("databaseError", probe.error());
        }

        if (!probe.valid()) {
            transition(DatabasePlatformState.DEGRADED, "DATABASE_CONNECTION_UNAVAILABLE");
            details.put("reasonCode", "DATABASE_CONNECTION_UNAVAILABLE");
            details.put("recommendedAction", "Check PostgreSQL reachability and credentials; readiness remains fail-closed");
            appendLifecycle(details);
            return details;
        }

        if (factory != null) {
            details.put("mappedStatementCount", factory.getConfiguration().getMappedStatementNames().size());
            details.put("mapUnderscoreToCamelCase", factory.getConfiguration().isMapUnderscoreToCamelCase());
        }

        if (flyway != null) {
            inspectFlyway(flyway, details);
        }

        String requirementFailure = requiredInfrastructureFailure(dataSource, factory, flyway, details);
        if (requirementFailure != null) {
            transition(DatabasePlatformState.DEGRADED, requirementFailure);
            details.put("reasonCode", requirementFailure);
            details.put("recommendedAction", "Restore required database infrastructure before accepting traffic");
        } else {
            transition(DatabasePlatformState.READY, "DATABASE_READY");
            details.put("reasonCode", "DATABASE_READY");
        }
        appendLifecycle(details);
        return details;
    }

    private ProbeResult connectionProbe(DataSource dataSource) {
        Instant now = Instant.now();
        ProbeResult existing = lastProbe;
        if (existing != null && now.isBefore(nextProbeAt)) {
            return existing.asCached();
        }

        synchronized (probeMonitor) {
            now = Instant.now();
            existing = lastProbe;
            if (existing != null && now.isBefore(nextProbeAt)) {
                return existing.asCached();
            }

            if (state == DatabasePlatformState.CONFIGURED || state == DatabasePlatformState.UNCONFIGURED) {
                transition(DatabasePlatformState.CONNECTING, "DATABASE_PROBE_STARTED");
            }
            ProbeResult fresh = inspectDataSource(dataSource, now);
            lastProbe = fresh;
            Duration delay = fresh.valid() ? properties.getProbeInterval() : properties.getDegradedProbeInterval();
            nextProbeAt = now.plus(delay);
            return fresh;
        }
    }

    private ProbeResult inspectDataSource(DataSource dataSource, Instant probedAt) {
        Duration timeout = properties.getValidationTimeout();
        int seconds = Math.max(1, (int) Math.ceil(timeout.toMillis() / 1000.0));
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(seconds);
            if (!valid) {
                return new ProbeResult(false, null, null, "Connection validation returned false", probedAt, false);
            }
            return new ProbeResult(
                    true,
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion(),
                    null,
                    probedAt,
                    false);
        } catch (Exception exception) {
            return new ProbeResult(
                    false,
                    null,
                    null,
                    exception.getClass().getSimpleName() + ": " + safeMessage(exception),
                    probedAt,
                    false);
        }
    }

    private void inspectFlyway(Flyway flyway, Map<String, Object> details) {
        try {
            MigrationInfo current = flyway.info().current();
            details.put("flywayCurrentVersion", current == null || current.getVersion() == null
                    ? null
                    : current.getVersion().getVersion());
            details.put("flywayCurrentDescription", current == null ? null : current.getDescription());
        } catch (Exception exception) {
            details.put("flywayError", exception.getClass().getSimpleName() + ": " + safeMessage(exception));
        }
    }

    private String requiredInfrastructureFailure(DataSource dataSource,
                                                 SqlSessionFactory factory,
                                                 Flyway flyway,
                                                 Map<String, Object> details) {
        if (properties.isRequireDataSource() && dataSource == null) {
            return "DATABASE_DATASOURCE_UNAVAILABLE";
        }
        if (properties.isRequireSqlSessionFactory() && factory == null) {
            return "DATABASE_MYBATIS_UNAVAILABLE";
        }
        if (properties.isRequireFlyway() && flyway == null) {
            return "DATABASE_FLYWAY_UNAVAILABLE";
        }
        if (details.containsKey("flywayError")) {
            return "DATABASE_FLYWAY_INSPECTION_FAILED";
        }
        return null;
    }

    private boolean databaseConfigured() {
        if (!pgEnabled()) {
            return false;
        }
        String url = environment.getProperty("pg.single.url");
        String username = environment.getProperty("pg.single.username");
        return hasText(url) && hasText(username);
    }

    private boolean pgEnabled() {
        return environment.getProperty("pg.enabled", Boolean.class, Boolean.FALSE);
    }

    private void appendLifecycle(Map<String, Object> details) {
        details.put("state", state.name());
        details.put("lastTransitionAt", lastTransitionAt.toString());
        details.put("transitionCount", transitionCount);
        Instant next = nextProbeAt;
        if (next != null && !Instant.EPOCH.equals(next)) {
            details.put("nextConnectionProbeAt", next.toString());
        }
    }

    private void transition(DatabasePlatformState next, String reasonCode) {
        DatabasePlatformState previous = state;
        if (previous == next) {
            return;
        }
        state = Objects.requireNonNull(next, "next");
        lastTransitionAt = Instant.now();
        transitionCount++;
        if (next == DatabasePlatformState.DEGRADED) {
            log.warn("database_platform_state_transition previous={} current={} reasonCode={}", previous, next, reasonCode);
        } else {
            log.info("database_platform_state_transition previous={} current={} reasonCode={}", previous, next, reasonCode);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "no message" : message;
    }

    private static <T> T first(ObjectProvider<T> provider) {
        T uniqueOrPrimary = provider.getIfUnique();
        return uniqueOrPrimary != null
                ? uniqueOrPrimary
                : provider.orderedStream().findFirst().orElse(null);
    }

    private record ProbeResult(boolean valid,
                               String databaseProduct,
                               String databaseVersion,
                               String error,
                               Instant probedAt,
                               boolean cached) {
        ProbeResult asCached() {
            return cached ? this : new ProbeResult(valid, databaseProduct, databaseVersion, error, probedAt, true);
        }
    }
}
