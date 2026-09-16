package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.Instant; import java.util.concurrent.*;
/** Process-local ingress guard. A shared gateway limiter may additionally enforce a cluster-wide ceiling. */
public class ProviderWebhookRateLimiter {
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 public boolean tryAcquire(String key,int limit,Instant now){long minute=now.getEpochSecond()/60;Window w=windows.compute(key,(k,old)->old==null||old.minute!=minute?new Window(minute,new java.util.concurrent.atomic.AtomicInteger()):old);return w.count.incrementAndGet()<=Math.max(1,limit);} private record Window(long minute,java.util.concurrent.atomic.AtomicInteger count){}
}
