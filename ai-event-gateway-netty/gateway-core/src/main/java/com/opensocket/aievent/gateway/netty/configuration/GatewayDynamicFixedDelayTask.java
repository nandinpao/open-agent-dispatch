package com.opensocket.aievent.gateway.netty.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.scheduling.TaskScheduler;

/** Gateway-local self-rescheduling task implementing the HOT_NEXT_CYCLE contract. */
public final class GatewayDynamicFixedDelayTask {
    private final TaskScheduler scheduler;
    private final String taskName;
    private final Runnable task;
    private final Supplier<Duration> initialDelaySupplier;
    private final Supplier<Duration> delaySupplier;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();

    public GatewayDynamicFixedDelayTask(
            TaskScheduler scheduler,
            String taskName,
            Runnable task,
            Supplier<Duration> delaySupplier) {
        this(scheduler, taskName, task, delaySupplier, delaySupplier);
    }

    public GatewayDynamicFixedDelayTask(
            TaskScheduler scheduler,
            String taskName,
            Runnable task,
            Supplier<Duration> initialDelaySupplier,
            Supplier<Duration> delaySupplier) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.taskName = Objects.requireNonNull(taskName, "taskName");
        this.task = Objects.requireNonNull(task, "task");
        this.initialDelaySupplier = Objects.requireNonNull(initialDelaySupplier, "initialDelaySupplier");
        this.delaySupplier = Objects.requireNonNull(delaySupplier, "delaySupplier");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        long lifecycle = generation.incrementAndGet();
        try { scheduleNext(resolve(initialDelaySupplier.get(), "initial"), lifecycle); }
        catch (RuntimeException ex) { fail(lifecycle); throw ex; }
    }

    public void stop() {
        running.set(false);
        generation.incrementAndGet();
        ScheduledFuture<?> future = scheduled.getAndSet(null);
        if (future != null) future.cancel(false);
    }

    public boolean isRunning() { return running.get(); }

    private void execute(long lifecycle) {
        if (!current(lifecycle)) return;
        try { task.run(); }
        finally {
            if (current(lifecycle)) {
                try { scheduleNext(resolve(delaySupplier.get(), "fixed"), lifecycle); }
                catch (RuntimeException ex) { fail(lifecycle); }
            }
        }
    }

    private void scheduleNext(Duration delay, long lifecycle) {
        if (!current(lifecycle)) return;
        ScheduledFuture<?> future = scheduler.schedule(() -> execute(lifecycle), Instant.now().plus(delay));
        if (future == null) throw new IllegalStateException("DYNAMIC_SCHEDULER_REJECTED " + taskName);
        if (!current(lifecycle)) { future.cancel(false); return; }
        ScheduledFuture<?> previous = scheduled.getAndSet(future);
        if (previous != null && previous != future && !previous.isDone()) previous.cancel(false);
    }

    private boolean current(long lifecycle) { return running.get() && generation.get() == lifecycle; }

    private void fail(long lifecycle) {
        if (generation.compareAndSet(lifecycle, lifecycle + 1)) {
            running.set(false);
            ScheduledFuture<?> future = scheduled.getAndSet(null);
            if (future != null && !future.isDone()) future.cancel(false);
        }
    }

    private Duration resolve(Duration delay, String phase) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            throw new IllegalStateException("DYNAMIC_SCHEDULER_INVALID_DELAY " + taskName + " phase=" + phase);
        }
        return delay;
    }
}
