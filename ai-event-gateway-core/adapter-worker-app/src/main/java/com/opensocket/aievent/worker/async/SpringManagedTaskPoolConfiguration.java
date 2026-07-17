package com.opensocket.aievent.worker.async;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;

/**
 * Adds bounded operational metrics to Spring-managed executors. Context propagation is
 * supplied by Spring Boot's ContextPropagatingTaskDecorator when
 * spring.task.execution.propagate-context=true.
 */
@Configuration(proxyBeanMethods = false)
public class SpringManagedTaskPoolConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    TaskDecorator openDispatchTaskMetricsDecorator(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName) {
        return new InstrumentedTaskDecorator(registry, serviceName);
    }

    @Bean
    ThreadPoolTaskExecutorCustomizer openDispatchTaskExecutorRejectionMetrics(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName) {
        return executor -> executor.setRejectedExecutionHandler(
                countingRejectedExecutionHandler(registry, serviceName, "application"));
    }

    @Bean
    ThreadPoolTaskSchedulerCustomizer openDispatchTaskSchedulerRejectionMetrics(
            MeterRegistry registry,
            @Value("${spring.application.name}") String serviceName) {
        return scheduler -> {
            scheduler.setRejectedExecutionHandler(
                    countingRejectedExecutionHandler(registry, serviceName, "scheduler"));
            scheduler.setRemoveOnCancelPolicy(true);
        };
    }

    private static RejectedExecutionHandler countingRejectedExecutionHandler(
            MeterRegistry registry, String serviceName, String pool) {
        Counter rejected = Counter.builder("opendispatch.task.rejected")
                .description("Number of tasks rejected by a Spring-managed executor")
                .tag("service", serviceName)
                .tag("pool", pool)
                .register(registry);
        RejectedExecutionHandler delegate = new ThreadPoolExecutor.AbortPolicy();
        return (task, executor) -> {
            rejected.increment();
            delegate.rejectedExecution(task, executor);
        };
    }

    static final class InstrumentedTaskDecorator implements TaskDecorator {
        private final MeterRegistry registry;
        private final String serviceName;
        private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();
        private final Map<String, Timer> queueWaitTimers = new ConcurrentHashMap<>();
        private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();

        InstrumentedTaskDecorator(MeterRegistry registry, String serviceName) {
            this.registry = registry;
            this.serviceName = serviceName;
        }

        @Override
        public Runnable decorate(Runnable runnable) {
            long submittedNanos = System.nanoTime();
            return () -> {
                String pool = classifyPool(Thread.currentThread().getName());
                if ("application".equals(pool)) {
                    queueWaitTimer(pool).record(System.nanoTime() - submittedNanos, TimeUnit.NANOSECONDS);
                }
                Timer.Sample execution = Timer.start(this.registry);
                try {
                    runnable.run();
                }
                catch (RuntimeException | Error failure) {
                    failureCounter(pool).increment();
                    throw failure;
                }
                finally {
                    execution.stop(executionTimer(pool));
                }
            };
        }

        private String classifyPool(String threadName) {
            return threadName != null && threadName.contains("scheduler") ? "scheduler" : "application";
        }

        private Timer executionTimer(String pool) {
            return this.executionTimers.computeIfAbsent(pool, key -> Timer.builder("opendispatch.task.execution")
                    .description("Execution duration of Spring-managed tasks")
                    .tag("service", this.serviceName)
                    .tag("pool", key)
                    .register(this.registry));
        }

        private Timer queueWaitTimer(String pool) {
            return this.queueWaitTimers.computeIfAbsent(pool, key -> Timer.builder("opendispatch.task.queue.wait")
                    .description("Queue wait duration before a Spring-managed asynchronous task starts")
                    .tag("service", this.serviceName)
                    .tag("pool", key)
                    .register(this.registry));
        }

        private Counter failureCounter(String pool) {
            return this.failureCounters.computeIfAbsent(pool, key -> Counter.builder("opendispatch.task.failures")
                    .description("Number of Spring-managed tasks that failed")
                    .tag("service", this.serviceName)
                    .tag("pool", key)
                    .register(this.registry));
        }
    }
}
