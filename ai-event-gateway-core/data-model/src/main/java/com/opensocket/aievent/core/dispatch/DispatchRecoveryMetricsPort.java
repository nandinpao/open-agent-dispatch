package com.opensocket.aievent.core.dispatch;

/** Optional observability hook for append-only dispatch/recovery timeline events. */
public interface DispatchRecoveryMetricsPort {
    void recordDispatchRecoveryEvent(DispatchAttemptHistoryRecord record);

    static DispatchRecoveryMetricsPort noop() {
        return NoopDispatchRecoveryMetricsPort.INSTANCE;
    }

    final class NoopDispatchRecoveryMetricsPort implements DispatchRecoveryMetricsPort {
        private static final NoopDispatchRecoveryMetricsPort INSTANCE = new NoopDispatchRecoveryMetricsPort();
        private NoopDispatchRecoveryMetricsPort() {
        }
        @Override
        public void recordDispatchRecoveryEvent(DispatchAttemptHistoryRecord record) {
        }
    }
}
