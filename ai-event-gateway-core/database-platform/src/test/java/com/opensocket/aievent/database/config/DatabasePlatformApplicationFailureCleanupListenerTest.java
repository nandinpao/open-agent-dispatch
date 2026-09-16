package com.opensocket.aievent.database.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

class DatabasePlatformApplicationFailureCleanupListenerTest {

    @Test
    void closesAlreadyCreatedCloseableDataSourceSingleton() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CloseableDataSource dataSource = new CloseableDataSource();
        beanFactory.registerSingleton("dataSource", dataSource);

        int closed = DatabasePlatformApplicationFailureCleanupListener.closeInitializedDataSources(beanFactory);

        assertThat(closed).isEqualTo(1);
        assertThat(dataSource.closed).isTrue();
    }

    @Test
    void closesResolvedPoolInsideRoutingDataSourceWithoutCreatingNewBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CloseableDataSource target = new CloseableDataSource();
        TestRoutingDataSource routing = new TestRoutingDataSource();
        routing.setTargetDataSources(Map.of("primary", target));
        routing.setDefaultTargetDataSource(target);
        routing.afterPropertiesSet();
        beanFactory.registerSingleton("routingDataSource", routing);

        int closed = DatabasePlatformApplicationFailureCleanupListener.closeInitializedDataSources(beanFactory);

        assertThat(closed).isEqualTo(1);
        assertThat(target.closed).isTrue();
    }

    private static final class TestRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return "primary";
        }
    }

    private static final class CloseableDataSource extends AbstractDataSource implements AutoCloseable {
        private boolean closed;

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
