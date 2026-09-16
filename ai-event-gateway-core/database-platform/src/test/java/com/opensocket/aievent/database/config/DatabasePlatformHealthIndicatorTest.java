package com.opensocket.aievent.database.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.database.health.DatabasePlatformHealthIndicator;

class DatabasePlatformHealthIndicatorTest {

    @Test
    void setupTolerantUnconfiguredDatabaseIsOutOfServiceRatherThanUp() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        DatabasePlatformProperties properties = new DatabasePlatformProperties();
        properties.setLifecycleMode(DatabasePlatformLifecycleMode.SETUP_TOLERANT);
        DatabasePlatformRuntimeInspector inspector = new DatabasePlatformRuntimeInspector(
                beanFactory.getBeanProvider(DataSource.class),
                beanFactory.getBeanProvider(SqlSessionFactory.class),
                beanFactory.getBeanProvider(Flyway.class),
                properties,
                new MockEnvironment().withProperty("pg.enabled", "false"));
        inspector.afterSingletonsInstantiated();

        Health health = new DatabasePlatformHealthIndicator(inspector, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails().get("state")).isEqualTo("UNCONFIGURED");
    }
}
