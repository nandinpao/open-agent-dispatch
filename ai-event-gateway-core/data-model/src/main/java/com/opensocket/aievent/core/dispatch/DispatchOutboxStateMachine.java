package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Framework-neutral contract for the durable dispatch intent lifecycle. */
public final class DispatchOutboxStateMachine {
    private static final Map<DispatchOutboxStatus, Set<DispatchOutboxStatus>> ALLOWED =
            new EnumMap<>(DispatchOutboxStatus.class);

    static {
        ALLOWED.put(DispatchOutboxStatus.PENDING, EnumSet.of(DispatchOutboxStatus.CLAIMED));
        ALLOWED.put(DispatchOutboxStatus.CLAIMED, EnumSet.of(
                DispatchOutboxStatus.DISPATCHING,
                DispatchOutboxStatus.FAILED_RETRYABLE,
                DispatchOutboxStatus.ABANDONED,
                DispatchOutboxStatus.BLOCKED,
                DispatchOutboxStatus.DEAD_LETTER));
        ALLOWED.put(DispatchOutboxStatus.DISPATCHING, EnumSet.of(
                DispatchOutboxStatus.ACKNOWLEDGED,
                DispatchOutboxStatus.FAILED_RETRYABLE,
                DispatchOutboxStatus.ABANDONED,
                DispatchOutboxStatus.BLOCKED,
                DispatchOutboxStatus.DEAD_LETTER));
        ALLOWED.put(DispatchOutboxStatus.FAILED_RETRYABLE, EnumSet.of(
                DispatchOutboxStatus.CLAIMED,
                DispatchOutboxStatus.ABANDONED,
                DispatchOutboxStatus.BLOCKED,
                DispatchOutboxStatus.DEAD_LETTER));
        ALLOWED.put(DispatchOutboxStatus.ACKNOWLEDGED, EnumSet.noneOf(DispatchOutboxStatus.class));
        ALLOWED.put(DispatchOutboxStatus.ABANDONED, EnumSet.noneOf(DispatchOutboxStatus.class));
        ALLOWED.put(DispatchOutboxStatus.BLOCKED, EnumSet.noneOf(DispatchOutboxStatus.class));
        ALLOWED.put(DispatchOutboxStatus.DEAD_LETTER, EnumSet.noneOf(DispatchOutboxStatus.class));
    }

    private DispatchOutboxStateMachine() {}

    public static boolean canTransition(DispatchOutboxStatus source, DispatchOutboxStatus target) {
        return source != null && target != null && ALLOWED.getOrDefault(source, Set.of()).contains(target);
    }

    public static void requireTransition(DispatchOutboxStatus source, DispatchOutboxStatus target) {
        if (!canTransition(source, target)) {
            throw new IllegalStateException("Invalid dispatch outbox transition: " + source + " -> " + target);
        }
    }

    public static boolean leaseExpired(OffsetDateTime claimUntil, OffsetDateTime now) {
        return claimUntil != null && now != null && !claimUntil.isAfter(now);
    }
}
