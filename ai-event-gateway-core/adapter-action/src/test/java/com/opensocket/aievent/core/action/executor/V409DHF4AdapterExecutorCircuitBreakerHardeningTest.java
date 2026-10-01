package com.opensocket.aievent.core.action.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/** V40-9D-HF4 atomic-state and deterministic-time proof for Adapter Executor circuit breaking. */
class V409DHF4AdapterExecutorCircuitBreakerHardeningTest {

    @Test
    void concurrentFailuresReachThresholdWithoutLostUpdates() throws Exception {
        AdapterActionExecutionProperties startup = startup(1000, Duration.ofMinutes(1));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T08:00:00Z"));
        AdapterExecutorCircuitBreaker breaker = breaker(startup, clock);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 1000; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    breaker.recordFailure("mcp-http-executor");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(breaker.isOpen("mcp-http-executor")).isTrue();
        Map<?, ?> state = (Map<?, ?>) breaker.snapshot().get("mcp-http-executor");
        assertThat(state.get("failureCount")).isEqualTo(1000);
        assertThat(state.get("openUntil")).isEqualTo("2026-09-21T08:01Z");
    }

    @Test
    void expiryUsesInjectedClockAndResetsAtomicallyAtBoundary() {
        AdapterActionExecutionProperties startup = startup(2, Duration.ofMinutes(1));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T08:00:00Z"));
        AdapterExecutorCircuitBreaker breaker = breaker(startup, clock);

        breaker.recordFailure("mcp-http-executor");
        breaker.recordFailure("mcp-http-executor");
        assertThat(breaker.isOpen("mcp-http-executor")).isTrue();
        assertThat(breaker.openUntil("mcp-http-executor")).hasToString("2026-09-21T08:01Z");

        clock.advance(Duration.ofSeconds(59));
        assertThat(breaker.isOpen("mcp-http-executor")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.isOpen("mcp-http-executor")).isFalse();
        assertThat(breaker.openUntil("mcp-http-executor")).isNull();

        breaker.recordFailure("mcp-http-executor");
        Map<?, ?> state = (Map<?, ?>) breaker.snapshot().get("mcp-http-executor");
        assertThat(state.get("failureCount")).isEqualTo(1);
        assertThat(state.get("openUntil")).isEqualTo("");
    }

    @Test
    void failuresWhileOpenDoNotExtendTheOpenWindow() {
        AdapterActionExecutionProperties startup = startup(1, Duration.ofMinutes(1));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T08:00:00Z"));
        AdapterExecutorCircuitBreaker breaker = breaker(startup, clock);

        breaker.recordFailure("mcp-http-executor");
        var original = breaker.openUntil("mcp-http-executor");
        clock.advance(Duration.ofSeconds(30));
        breaker.recordFailure("mcp-http-executor");

        assertThat(breaker.openUntil("mcp-http-executor")).isEqualTo(original);
        Map<?, ?> state = (Map<?, ?>) breaker.snapshot().get("mcp-http-executor");
        assertThat(state.get("failureCount")).isEqualTo(1);
    }

    private static AdapterActionExecutionProperties startup(int threshold, Duration openDuration) {
        AdapterActionExecutionProperties startup = new AdapterActionExecutionProperties();
        startup.getCircuitBreaker().setEnabled(true);
        startup.getCircuitBreaker().setFailureThreshold(threshold);
        startup.getCircuitBreaker().setOpenDuration(openDuration);
        return startup;
    }

    private static AdapterExecutorCircuitBreaker breaker(AdapterActionExecutionProperties startup, Clock clock) {
        return new AdapterExecutorCircuitBreaker(
                startup,
                new AdapterExecutorRuntimeConfigurationView(startup, null),
                clock);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("HF4 test clock is UTC-only");
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
