package com.opensocket.aievent.database.config;

import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class DatabasePlatformRuntimeInspector implements SmartInitializingSingleton {
    private final ObjectProvider<DataSource> dataSources;
    private final ObjectProvider<SqlSessionFactory> sqlSessionFactories;
    private final ObjectProvider<Flyway> flyways;
    private final DatabasePlatformProperties properties;

    public DatabasePlatformRuntimeInspector(ObjectProvider<DataSource> dataSources,
                                            ObjectProvider<SqlSessionFactory> sqlSessionFactories,
                                            ObjectProvider<Flyway> flyways,
                                            DatabasePlatformProperties properties) {
        this.dataSources = dataSources;
        this.sqlSessionFactories = sqlSessionFactories;
        this.flyways = flyways;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.isValidateOnStartup()) {
            validateRequiredInfrastructure();
        }
    }

    public void validateRequiredInfrastructure() {
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
        DataSource dataSource = first(dataSources);
        details.put("dataSourceAvailable", dataSource != null);
        if (dataSource != null) {
            inspectDataSource(dataSource, details);
        }

        SqlSessionFactory factory = first(sqlSessionFactories);
        details.put("sqlSessionFactoryAvailable", factory != null);
        if (factory != null) {
            details.put("mappedStatementCount", factory.getConfiguration().getMappedStatementNames().size());
            details.put("mapUnderscoreToCamelCase", factory.getConfiguration().isMapUnderscoreToCamelCase());
        }

        Flyway flyway = first(flyways);
        details.put("flywayAvailable", flyway != null);
        if (flyway != null) {
            try {
                MigrationInfo current = flyway.info().current();
                details.put("flywayCurrentVersion", current == null || current.getVersion() == null
                        ? null
                        : current.getVersion().getVersion());
                details.put("flywayCurrentDescription", current == null ? null : current.getDescription());
            } catch (Exception exception) {
                details.put("flywayError", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
        return details;
    }

    private static <T> T first(ObjectProvider<T> provider) {
        T uniqueOrPrimary = provider.getIfUnique();
        return uniqueOrPrimary != null
                ? uniqueOrPrimary
                : provider.orderedStream().findFirst().orElse(null);
    }

    private void inspectDataSource(DataSource dataSource, Map<String, Object> details) {
        Duration timeout = properties.getValidationTimeout();
        int seconds = Math.max(1, (int) Math.ceil(timeout.toMillis() / 1000.0));
        try (Connection connection = dataSource.getConnection()) {
            details.put("connectionValid", connection.isValid(seconds));
            details.put("databaseProduct", connection.getMetaData().getDatabaseProductName());
            details.put("databaseVersion", connection.getMetaData().getDatabaseProductVersion());
        } catch (Exception exception) {
            details.put("connectionValid", false);
            details.put("databaseError", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }
}
