package com.opensocket.aievent.database.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class DatabasePlatformRuntimeInspectorTest {

    @Test
    void setupTolerantModeReportsUnconfiguredWithoutDemandingInfrastructure() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        DatabasePlatformProperties properties = new DatabasePlatformProperties();
        properties.setLifecycleMode(DatabasePlatformLifecycleMode.SETUP_TOLERANT);
        properties.setValidateOnStartup(true);
        MockEnvironment environment = new MockEnvironment().withProperty("pg.enabled", "false");

        DatabasePlatformRuntimeInspector inspector = inspector(beanFactory, properties, environment);
        inspector.afterSingletonsInstantiated();

        Map<String, Object> snapshot = inspector.snapshot();
        assertThat(snapshot.get("state")).isEqualTo("UNCONFIGURED");
        assertThat(snapshot.get("reasonCode")).isEqualTo("DATABASE_UNCONFIGURED");
        assertThat(snapshot.get("databaseConfigured")).isEqualTo(false);
    }

    @Test
    void requiredModeRejectsExplicitlyUnconfiguredDatabase() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        DatabasePlatformProperties properties = new DatabasePlatformProperties();
        properties.setLifecycleMode(DatabasePlatformLifecycleMode.REQUIRED);
        properties.setValidateOnStartup(true);
        MockEnvironment environment = new MockEnvironment().withProperty("pg.enabled", "false");

        DatabasePlatformRuntimeInspector inspector = inspector(beanFactory, properties, environment);

        assertThatThrownBy(inspector::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database platform is REQUIRED");
    }

    @Test
    void successfulConnectionProbeIsCachedInsideProbeInterval() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("test");
        beanFactory.registerSingleton("testDataSource", dataSource);

        DatabasePlatformProperties properties = new DatabasePlatformProperties();
        properties.setLifecycleMode(DatabasePlatformLifecycleMode.SETUP_TOLERANT);
        properties.setRequireSqlSessionFactory(false);
        properties.setRequireFlyway(false);
        properties.setProbeInterval(Duration.ofMinutes(1));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pg.enabled", "true")
                .withProperty("pg.single.url", "jdbc:postgresql://db/test")
                .withProperty("pg.single.username", "test");

        DatabasePlatformRuntimeInspector inspector = inspector(beanFactory, properties, environment);
        inspector.afterSingletonsInstantiated();

        Map<String, Object> first = inspector.snapshot();
        Map<String, Object> second = inspector.snapshot();

        assertThat(first.get("state")).isEqualTo("READY");
        assertThat(first.get("connectionProbeCached")).isEqualTo(false);
        assertThat(second.get("state")).isEqualTo("READY");
        assertThat(second.get("connectionProbeCached")).isEqualTo(true);
        verify(dataSource, times(1)).getConnection();
    }

    private static DatabasePlatformRuntimeInspector inspector(DefaultListableBeanFactory beanFactory,
                                                              DatabasePlatformProperties properties,
                                                              MockEnvironment environment) {
        return new DatabasePlatformRuntimeInspector(
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(SqlSessionFactory.class),
                beanFactory.getBeanProvider(Flyway.class),
                properties,
                environment);
    }
}
