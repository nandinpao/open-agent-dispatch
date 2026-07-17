package com.opensocket.aievent.core.observability;

import java.time.OffsetDateTime;
import java.util.List;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;

public record RecoveryOperationMetricsSnapshot(
        String status,
        OffsetDateTime generatedAt,
        OffsetDateTime windowStart,
        String window,
        int historyLimit,
        long totalEvents,
        RecoveryOperationTotals totals,
        List<RecoveryMetricBucket> byEventType,
        List<RecoveryMetricBucket> byAgent,
        List<RecoveryAlertEvaluation> alerts,
        List<DispatchAttemptHistoryRecord> recentCriticalEvents,
        RecoveryAlertPolicyView alertPolicy
) {
    public record RecoveryOperationTotals(
            long gatewayDelivered,
            long gatewayRetryWaiting,
            long runtimeDeliveryFailed,
            long runtimeBackoffApplied,
            long taskRequeued,
            long delayedRequeueScheduled,
            long delayedRequeueClaimed,
            long delayedRequeueFailed,
            long recoveryExhausted,
            long deadLettered
    ) {}

    public record RecoveryMetricBucket(
            String key,
            long count,
            OffsetDateTime latestOccurredAt
    ) {}

    public record RecoveryAlertEvaluation(
            String code,
            String severity,
            String metric,
            long observed,
            long warningThreshold,
            long criticalThreshold,
            String message
    ) {}

    public record RecoveryAlertPolicyView(
            boolean enabled,
            String window,
            int historyLimit,
            int runtimeFailureWarningThreshold,
            int runtimeFailureCriticalThreshold,
            int delayedRequeueWarningThreshold,
            int delayedRequeueCriticalThreshold,
            int deadLetterWarningThreshold,
            int deadLetterCriticalThreshold,
            int scannerFailureWarningThreshold,
            int scannerFailureCriticalThreshold,
            int recoveryExhaustedWarningThreshold,
            int recoveryExhaustedCriticalThreshold
    ) {}
}
