package com.opensocket.aievent.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.scheduling.TaskScheduler;

/** Adapter-worker-local dynamic scheduler; keeps this deployable independent from Core internals. */
final class WorkerDynamicFixedDelayTask {
    private final TaskScheduler scheduler;private final String name;private final Runnable task;private final Supplier<Duration> delaySupplier;
    private final AtomicBoolean running=new AtomicBoolean();private final AtomicLong generation=new AtomicLong();private final AtomicReference<ScheduledFuture<?>> future=new AtomicReference<>();
    WorkerDynamicFixedDelayTask(TaskScheduler scheduler,String name,Runnable task,Supplier<Duration> delaySupplier){this.scheduler=Objects.requireNonNull(scheduler);this.name=Objects.requireNonNull(name);this.task=Objects.requireNonNull(task);this.delaySupplier=Objects.requireNonNull(delaySupplier);}
    void start(){if(!running.compareAndSet(false,true))return;long g=generation.incrementAndGet();schedule(delay(),g);}
    void stop(){running.set(false);generation.incrementAndGet();ScheduledFuture<?> f=future.getAndSet(null);if(f!=null)f.cancel(false);}
    private void execute(long g){if(!current(g))return;try{task.run();}finally{if(current(g))schedule(delay(),g);}}
    private void schedule(Duration d,long g){if(!current(g))return;ScheduledFuture<?> next=scheduler.schedule(()->execute(g),Instant.now().plus(d));if(next==null)throw new IllegalStateException("DYNAMIC_SCHEDULER_REJECTED "+name);ScheduledFuture<?> old=future.getAndSet(next);if(old!=null&&old!=next&&!old.isDone())old.cancel(false);}
    private Duration delay(){Duration d=delaySupplier.get();if(d==null||d.isZero()||d.isNegative())throw new IllegalStateException("DYNAMIC_SCHEDULER_INVALID_DELAY "+name);return d;}
    private boolean current(long g){return running.get()&&generation.get()==g;}
}
