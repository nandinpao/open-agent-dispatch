package com.opensocket.aievent.database.config;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Closes already-created Hikari pools as soon as Spring reports a startup failure.
 *
 * <p>This listener is registered directly on {@code SpringApplication}, not as a regular bean, so it
 * still runs when context refresh fails before listener beans can be fully registered. It never asks
 * the BeanFactory to create a new bean; it only examines existing singleton DataSources and any
 * resolved targets owned by an {@link AbstractRoutingDataSource}. This prevents Hikari housekeeper/
 * connection-adder threads from obscuring the actual startup exception with servlet-container
 * memory-leak warnings.</p>
 */
public final class DatabasePlatformApplicationFailureCleanupListener
        implements ApplicationListener<ApplicationFailedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DatabasePlatformApplicationFailureCleanupListener.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        if (context == null) {
            return;
        }

        int closed = closeInitializedDataSources(context.getBeanFactory());
        if (closed > 0) {
            log.info(
                    "database_platform_startup_failure_cleanup closedPools={} rootFailure={}",
                    closed,
                    event.getException().getClass().getSimpleName());
        }
    }

    static int closeInitializedDataSources(ConfigurableListableBeanFactory beanFactory) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int closed = 0;
        for (String singletonName : beanFactory.getSingletonNames()) {
            Object singleton = beanFactory.getSingleton(singletonName);
            if (singleton instanceof DataSource dataSource) {
                closed += closeDataSourceTree(dataSource, singletonName, visited);
            }
        }
        return closed;
    }

    private static int closeDataSourceTree(DataSource dataSource, String beanName, Set<Object> visited) {
        if (!visited.add(dataSource)) {
            return 0;
        }

        int closed = 0;
        if (dataSource instanceof AbstractRoutingDataSource routingDataSource) {
            try {
                Map<Object, DataSource> targets = routingDataSource.getResolvedDataSources();
                for (Map.Entry<Object, DataSource> entry : targets.entrySet()) {
                    closed += closeDataSourceTree(
                            entry.getValue(),
                            beanName + "[" + String.valueOf(entry.getKey()) + "]",
                            visited);
                }
                DataSource defaultTarget = routingDataSource.getResolvedDefaultDataSource();
                if (defaultTarget != null) {
                    closed += closeDataSourceTree(defaultTarget, beanName + "[default]", visited);
                }
            } catch (IllegalStateException ignored) {
                // Routing DataSource was created but not initialized far enough to resolve its targets.
            }
        }

        if (isCloseablePool(dataSource) && closePool(dataSource, beanName)) {
            closed++;
        }
        return closed;
    }

    private static boolean isCloseablePool(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            return true;
        }
        try {
            return dataSource.getClass().getMethod("close").getParameterCount() == 0;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean closePool(Object dataSource, String beanName) {
        try {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
                return true;
            }
            Method close = dataSource.getClass().getMethod("close");
            if (close.getParameterCount() == 0) {
                close.invoke(dataSource);
                return true;
            }
        } catch (NoSuchMethodException ignored) {
            // The DataSource exposes no close contract.
        } catch (Exception exception) {
            log.warn(
                    "database_platform_startup_failure_cleanup_failed bean={} type={} error={}",
                    beanName,
                    dataSource.getClass().getName(),
                    exception.toString());
        }
        return false;
    }
}
