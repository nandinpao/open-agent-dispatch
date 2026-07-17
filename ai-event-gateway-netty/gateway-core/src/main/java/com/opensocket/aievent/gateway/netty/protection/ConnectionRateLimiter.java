package com.opensocket.aievent.gateway.netty.protection;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple fixed-window per-key rate limiter for Netty TCP and WebSocket ingress messages. */
@Component
public class ConnectionRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxPerMinute) {
        if (key == null || key.isBlank() || maxPerMinute <= 0) {
            return true;
        }
        var now = Instant.now().toEpochMilli();
        cleanup(now);
        var counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStartedAt >= WINDOW_MILLIS) {
                return new WindowCounter(now);
            }
            return existing;
        });
        return counter.count.incrementAndGet() <= maxPerMinute;
    }

    private void cleanup(long now) {
        if (counters.size() < 1024) {
            return;
        }
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().windowStartedAt >= WINDOW_MILLIS * 2) {
                iterator.remove();
            }
        }
    }

    private static final class WindowCounter {
        private final long windowStartedAt;
        private final AtomicInteger count = new AtomicInteger();

        private WindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
