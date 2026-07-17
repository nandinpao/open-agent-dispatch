package com.opensocket.aievent.core.dispatch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/** TODO 15-D task-level retry backoff policy with bounded exponential backoff. */
@Component
public class TaskRetryBackoffPolicy {
    private final DispatchProperties properties;

    public TaskRetryBackoffPolicy(DispatchProperties properties) {
        this.properties = properties == null ? new DispatchProperties() : properties;
    }

    public OffsetDateTime nextRetryAt(int nextAttemptNo, OffsetDateTime now) {
        return nextRetryAt(nextAttemptNo, now, null);
    }

    public OffsetDateTime nextRetryAt(int nextAttemptNo, OffsetDateTime now, String stableJitterKey) {
        OffsetDateTime at = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        return at.plus(delayForAttempt(nextAttemptNo, stableJitterKey));
    }

    public Duration delayForAttempt(int nextAttemptNo) {
        return delayForAttempt(nextAttemptNo, null);
    }

    public Duration delayForAttempt(int nextAttemptNo, String stableJitterKey) {
        int attempt = Math.max(1, nextAttemptNo);
        long multiplier = 1L << Math.max(0, Math.min(attempt - 1, 10));
        Duration initial = properties.getRetry().getInitialBackoff();
        Duration max = properties.getRetry().getMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        Duration capped = candidate.compareTo(max) > 0 ? max : candidate;
        return applyDeterministicJitter(capped, stableJitterKey, properties.getRetry().getJitterPercent());
    }

    public Duration applyDeterministicJitter(Duration base, String stableJitterKey, int jitterPercent) {
        if (base == null || base.isZero() || base.isNegative() || jitterPercent <= 0 || stableJitterKey == null || stableJitterKey.isBlank()) {
            return base == null ? Duration.ZERO : base;
        }
        long millis = Math.max(1L, base.toMillis());
        long spread = Math.max(1L, millis * Math.min(jitterPercent, 100) / 100L);
        int bucket = Math.floorMod(stableJitterKey.hashCode(), 201) - 100;
        long delta = spread * bucket / 100L;
        long jittered = Math.max(1L, millis + delta);
        return Duration.ofMillis(jittered);
    }
}
