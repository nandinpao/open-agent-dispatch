package com.opensocket.aievent.core.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SpringManagedTaskPoolConfigurationTest {

    @Test
    void recordsApplicationQueueWaitExecutionAndFailureMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpringManagedTaskPoolConfiguration.InstrumentedTaskDecorator decorator =
                new SpringManagedTaskPoolConfiguration.InstrumentedTaskDecorator(registry, "core-test");
        String originalName = Thread.currentThread().getName();
        Thread.currentThread().setName("core-async-test");
        try {
            decorator.decorate(() -> { }).run();
            assertThatThrownBy(() -> decorator.decorate(() -> {
                throw new IllegalStateException("boom");
            }).run()).isInstanceOf(IllegalStateException.class);
        }
        finally {
            Thread.currentThread().setName(originalName);
        }

        assertThat(registry.get("opendispatch.task.queue.wait").tag("pool", "application").timer().count())
                .isEqualTo(2);
        assertThat(registry.get("opendispatch.task.execution").tag("pool", "application").timer().count())
                .isEqualTo(2);
        assertThat(registry.get("opendispatch.task.failures").tag("pool", "application").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsSchedulerExecutionWithoutMisreportingStartupDelayAsQueueWait() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpringManagedTaskPoolConfiguration.InstrumentedTaskDecorator decorator =
                new SpringManagedTaskPoolConfiguration.InstrumentedTaskDecorator(registry, "core-test");
        String originalName = Thread.currentThread().getName();
        Thread.currentThread().setName("core-scheduler-test");
        try {
            decorator.decorate(() -> { }).run();
        }
        finally {
            Thread.currentThread().setName(originalName);
        }

        assertThat(registry.get("opendispatch.task.execution").tag("pool", "scheduler").timer().count())
                .isEqualTo(1);
        assertThat(registry.find("opendispatch.task.queue.wait").tag("pool", "scheduler").timer()).isNull();
    }

    @Test
    void rejectionCustomizerIncrementsBoundedCounterAndPreservesAbortPolicy() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpringManagedTaskPoolConfiguration configuration = new SpringManagedTaskPoolConfiguration();
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        configuration.openDispatchTaskExecutorRejectionMetrics(registry, "core-test").customize(executor);

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(RuntimeException.class);
            assertThat(registry.get("opendispatch.task.rejected").tag("pool", "application").counter().count())
                    .isEqualTo(1.0);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
