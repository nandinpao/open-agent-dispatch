package com.opensocket.aievent.core.configuration.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.scheduling.TaskScheduler;

/**
 * Self-rescheduling fixed-delay task whose delay is resolved after every completed cycle.
 *
 * <p>The current wait is never shortened retroactively. A configuration change becomes effective
 * when the next cycle is scheduled, which is the V40-8 HOT_NEXT_CYCLE contract.</p>
 *
 * <p>V40-8.2 adds a lifecycle generation so a future created by an older stop/start generation
 * can never execute or reschedule after a restart. Scheduling/configuration failures fail the
 * task closed instead of leaving {@code running=true} with no future.</p>
 */
public final class DynamicFixedDelayTask {
    private final TaskScheduler scheduler;
    private final String taskName;
    private final Runnable task;
    private final Supplier<Duration> initialDelaySupplier;
    private final Supplier<Duration> delaySupplier;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();
    private final AtomicReference<RuntimeException> lastFailure = new AtomicReference<>();

    public DynamicFixedDelayTask(TaskScheduler scheduler, String taskName, Runnable task, Supplier<Duration> delaySupplier) {
        this(scheduler, taskName, task, delaySupplier, delaySupplier);
    }

    /**
     * V41-C3B1 constructor for schedulers whose initial delay differs from the steady-state delay.
     * Both suppliers are resolved without I/O from the local runtime configuration view.
     */
    public DynamicFixedDelayTask(TaskScheduler scheduler, String taskName, Runnable task,
            Supplier<Duration> initialDelaySupplier, Supplier<Duration> delaySupplier) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.taskName = Objects.requireNonNull(taskName, "taskName");
        this.task = Objects.requireNonNull(task, "task");
        this.initialDelaySupplier = Objects.requireNonNull(initialDelaySupplier, "initialDelaySupplier");
        this.delaySupplier = Objects.requireNonNull(delaySupplier, "delaySupplier");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        long lifecycle=generation.incrementAndGet();
        lastFailure.set(null);
        try { scheduleNext(resolveInitialDelay(),lifecycle); }
        catch(RuntimeException ex){ failLifecycle(lifecycle,ex); throw ex; }
    }

    public void stop() {
        running.set(false);
        generation.incrementAndGet(); // invalidates already queued callbacks from the previous lifecycle
        ScheduledFuture<?> future = scheduled.getAndSet(null);
        if (future != null) future.cancel(false);
    }

    public boolean isRunning() { return running.get(); }
    public Duration currentDelay() { return resolveDelay(); }
    public Optional<RuntimeException> lastFailure(){return Optional.ofNullable(lastFailure.get());}

    private void executeCycle(long lifecycle) {
        if (!isCurrent(lifecycle)) return;
        try { task.run(); }
        finally {
            if (isCurrent(lifecycle)) {
                try { scheduleNext(resolveDelay(),lifecycle); }
                catch(RuntimeException ex){ failLifecycle(lifecycle,ex); }
            }
        }
    }

    private void scheduleNext(Duration delay,long lifecycle) {
        if (!isCurrent(lifecycle)) return;
        ScheduledFuture<?> future = scheduler.schedule(() -> executeCycle(lifecycle), Instant.now().plus(delay));
        if (future == null) throw new IllegalStateException("DYNAMIC_SCHEDULER_REJECTED " + taskName);
        if (!isCurrent(lifecycle)) { future.cancel(false); return; }
        ScheduledFuture<?> previous = scheduled.getAndSet(future);
        if (previous != null && previous != future && !previous.isDone()) previous.cancel(false);
    }

    private boolean isCurrent(long lifecycle){return running.get()&&generation.get()==lifecycle;}
    private void failLifecycle(long lifecycle,RuntimeException ex){
        if(generation.compareAndSet(lifecycle,lifecycle+1)){
            lastFailure.set(ex); running.set(false);
            ScheduledFuture<?> future=scheduled.getAndSet(null);if(future!=null&&!future.isDone())future.cancel(false);
        }
    }

    private Duration resolveInitialDelay() {
        return validateDelay(initialDelaySupplier.get(), "initial");
    }

    private Duration resolveDelay() {
        return validateDelay(delaySupplier.get(), "fixed");
    }

    private Duration validateDelay(Duration delay, String phase) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            throw new IllegalStateException("DYNAMIC_SCHEDULER_INVALID_DELAY " + taskName + " phase=" + phase);
        }
        return delay;
    }
}
