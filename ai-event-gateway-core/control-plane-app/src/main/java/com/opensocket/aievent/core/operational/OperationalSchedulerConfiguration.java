package com.opensocket.aievent.core.operational;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * PC-S6 isolates blocking scheduled workloads so remote A2A, dispatch, reconciliation and
 * projection jobs cannot starve one another on the shared Spring scheduler.
 *
 * <p>Pool sizes are operational starting points, not CPU-derived truths. They remain configurable
 * and must be tuned from the Stage 6 runtime evidence/SLO metrics.</p>
 */
@Configuration(proxyBeanMethods = false)
public class OperationalSchedulerConfiguration {

    @Bean(name = "dispatchOperationalScheduler")
    ThreadPoolTaskScheduler dispatchOperationalScheduler(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName,
            @Value("${opendispatch.operational.schedulers.dispatch.pool-size:2}") int poolSize) {
        return scheduler("dispatch", poolSize, registry, serviceName);
    }

    @Bean(name = "a2aRemoteOperationalScheduler")
    ThreadPoolTaskScheduler a2aRemoteOperationalScheduler(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName,
            @Value("${opendispatch.operational.schedulers.a2a-remote.pool-size:4}") int poolSize) {
        return scheduler("a2a-remote", poolSize, registry, serviceName);
    }

    @Bean(name = "reconciliationOperationalScheduler")
    ThreadPoolTaskScheduler reconciliationOperationalScheduler(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName,
            @Value("${opendispatch.operational.schedulers.reconciliation.pool-size:2}") int poolSize) {
        return scheduler("reconciliation", poolSize, registry, serviceName);
    }

    @Bean(name = "projectionOperationalScheduler")
    ThreadPoolTaskScheduler projectionOperationalScheduler(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName,
            @Value("${opendispatch.operational.schedulers.projection.pool-size:2}") int poolSize) {
        return scheduler("projection", poolSize, registry, serviceName);
    }

    @Bean(name = "maintenanceOperationalScheduler")
    ThreadPoolTaskScheduler maintenanceOperationalScheduler(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName,
            @Value("${opendispatch.operational.schedulers.maintenance.pool-size:2}") int poolSize) {
        return scheduler("maintenance", poolSize, registry, serviceName);
    }

    private static ThreadPoolTaskScheduler scheduler(
            String pool, int configuredSize, MeterRegistry registry, String serviceName) {
        int poolSize = Math.max(1, Math.min(configuredSize, 64));
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("core-" + pool + "-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setTaskDecorator(new SchedulerMetricsTaskDecorator(registry, serviceName, pool));

        Gauge.builder("opendispatch.scheduler.pool.size", scheduler, ThreadPoolTaskScheduler::getPoolSize)
                .description("Current operational scheduler pool size")
                .tag("service", serviceName)
                .tag("pool", pool)
                .register(registry);
        Gauge.builder("opendispatch.scheduler.active", scheduler, ThreadPoolTaskScheduler::getActiveCount)
                .description("Active threads in an operational scheduler")
                .tag("service", serviceName)
                .tag("pool", pool)
                .register(registry);
        Gauge.builder("opendispatch.scheduler.queue.depth", scheduler, OperationalSchedulerConfiguration::queueDepth)
                .description("Delayed or waiting callbacks currently held by the operational scheduler")
                .tag("service", serviceName)
                .tag("pool", pool)
                .register(registry);
        Gauge.builder("opendispatch.scheduler.saturation.ratio", scheduler, OperationalSchedulerConfiguration::saturation)
                .description("Active threads divided by current operational scheduler pool size")
                .tag("service", serviceName)
                .tag("pool", pool)
                .register(registry);
        return scheduler;
    }

    private static double queueDepth(ThreadPoolTaskScheduler scheduler) {
        try {
            return scheduler.getScheduledThreadPoolExecutor().getQueue().size();
        }
        catch (IllegalStateException notInitializedYet) {
            return 0.0d;
        }
    }

    private static double saturation(ThreadPoolTaskScheduler scheduler) {
        try {
            int size = scheduler.getPoolSize();
            return size == 0 ? 0.0d : (double) scheduler.getActiveCount() / size;
        }
        catch (IllegalStateException notInitializedYet) {
            return 0.0d;
        }
    }

    static final class SchedulerMetricsTaskDecorator implements TaskDecorator {
        private final MeterRegistry registry;
        private final String serviceName;
        private final String pool;
        private final Map<String, Timer> timers = new ConcurrentHashMap<>();
        private final Counter failures;

        SchedulerMetricsTaskDecorator(MeterRegistry registry, String serviceName, String pool) {
            this.registry = registry;
            this.serviceName = serviceName;
            this.pool = pool;
            this.failures = Counter.builder("opendispatch.scheduler.task.failures")
                    .description("Scheduled callback failures by isolated operational pool")
                    .tag("service", serviceName)
                    .tag("pool", pool)
                    .register(registry);
        }

        @Override
        public Runnable decorate(Runnable runnable) {
            return () -> {
                Timer.Sample sample = Timer.start(this.registry);
                try {
                    runnable.run();
                }
                catch (RuntimeException | Error failure) {
                    this.failures.increment();
                    throw failure;
                }
                finally {
                    sample.stop(executionTimer());
                }
            };
        }

        private Timer executionTimer() {
            return this.timers.computeIfAbsent(this.pool, ignored -> Timer.builder("opendispatch.scheduler.task.execution")
                    .description("Execution duration for callbacks on isolated operational schedulers")
                    .tag("service", this.serviceName)
                    .tag("pool", this.pool)
                    .publishPercentileHistogram()
                    .register(this.registry));
        }
    }
}
