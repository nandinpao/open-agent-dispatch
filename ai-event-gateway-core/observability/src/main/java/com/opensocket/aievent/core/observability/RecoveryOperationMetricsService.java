package com.opensocket.aievent.core.observability;

import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_DEAD_LETTERED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_DELAYED_REQUEUE_CLAIMED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_DELAYED_REQUEUE_FAILED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_DELAYED_REQUEUE_SCHEDULED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_GATEWAY_DELIVERED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_GATEWAY_RETRY_WAITING;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_RECOVERY_EXHAUSTED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_RUNTIME_BACKOFF_APPLIED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_RUNTIME_DELIVERY_FAILED;
import static com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService.EVENT_TASK_REQUEUED;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.observability.RecoveryOperationMetricsSnapshot.RecoveryAlertEvaluation;
import com.opensocket.aievent.core.observability.RecoveryOperationMetricsSnapshot.RecoveryAlertPolicyView;
import com.opensocket.aievent.core.observability.RecoveryOperationMetricsSnapshot.RecoveryMetricBucket;
import com.opensocket.aievent.core.observability.RecoveryOperationMetricsSnapshot.RecoveryOperationTotals;

@Service
public class RecoveryOperationMetricsService {
    private static final Set<String> CRITICAL_EVENT_TYPES = Set.of(
            EVENT_RUNTIME_DELIVERY_FAILED,
            EVENT_RUNTIME_BACKOFF_APPLIED,
            EVENT_DELAYED_REQUEUE_FAILED,
            EVENT_RECOVERY_EXHAUSTED,
            EVENT_DEAD_LETTERED
    );

    private final DispatchAttemptHistoryService attemptHistoryService;
    private final ObservabilityProperties properties;

    public RecoveryOperationMetricsService(DispatchAttemptHistoryService attemptHistoryService,
                                           ObservabilityProperties properties) {
        this.attemptHistoryService = attemptHistoryService;
        this.properties = properties == null ? new ObservabilityProperties() : properties;
    }

    public RecoveryOperationMetricsSnapshot snapshot(Duration requestedWindow, Integer requestedLimit) {
        ObservabilityProperties.RecoveryMetrics recovery = properties.getRecoveryMetrics();
        Duration window = normalizeWindow(requestedWindow == null ? recovery.getWindow() : requestedWindow);
        int limit = cap(requestedLimit == null ? recovery.getHistoryLimit() : requestedLimit);
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = generatedAt.minus(window);

        List<DispatchAttemptHistoryRecord> records = recovery.isEnabled()
                ? attemptHistoryService.findSince(windowStart, limit)
                : List.of();

        Map<String, Long> byEvent = countBy(records, DispatchAttemptHistoryRecord::getEventType);
        Map<String, Long> byAgent = countBy(records, record -> blankToNone(record.getAgentId()));
        Map<String, OffsetDateTime> latestByEvent = latestBy(records, DispatchAttemptHistoryRecord::getEventType);
        Map<String, OffsetDateTime> latestByAgent = latestBy(records, record -> blankToNone(record.getAgentId()));

        RecoveryOperationTotals totals = new RecoveryOperationTotals(
                count(byEvent, EVENT_GATEWAY_DELIVERED),
                count(byEvent, EVENT_GATEWAY_RETRY_WAITING),
                count(byEvent, EVENT_RUNTIME_DELIVERY_FAILED),
                count(byEvent, EVENT_RUNTIME_BACKOFF_APPLIED),
                count(byEvent, EVENT_TASK_REQUEUED),
                count(byEvent, EVENT_DELAYED_REQUEUE_SCHEDULED),
                count(byEvent, EVENT_DELAYED_REQUEUE_CLAIMED),
                count(byEvent, EVENT_DELAYED_REQUEUE_FAILED),
                count(byEvent, EVENT_RECOVERY_EXHAUSTED),
                count(byEvent, EVENT_DEAD_LETTERED)
        );

        List<RecoveryAlertEvaluation> alerts = evaluateAlerts(totals, recovery);
        String status = recovery.isEnabled() ? worstStatus(alerts) : "DISABLED";

        return new RecoveryOperationMetricsSnapshot(
                status,
                generatedAt,
                windowStart,
                window.toString(),
                limit,
                records.size(),
                totals,
                buckets(byEvent, latestByEvent, 50),
                buckets(byAgent, latestByAgent, 50),
                alerts,
                recentCritical(records, 20),
                policyView(recovery, window, limit)
        );
    }

    private List<RecoveryAlertEvaluation> evaluateAlerts(RecoveryOperationTotals totals,
                                                         ObservabilityProperties.RecoveryMetrics recovery) {
        return List.of(
                evaluate("runtime-delivery-failures", "runtimeDeliveryFailed", totals.runtimeDeliveryFailed(),
                        recovery.getRuntimeFailureWarningThreshold(), recovery.getRuntimeFailureCriticalThreshold(),
                        "Netty/runtime delivery failures are causing same-task requeue and agent runtime backoff."),
                evaluate("delayed-requeue-volume", "delayedRequeueScheduled", totals.delayedRequeueScheduled(),
                        recovery.getDelayedRequeueWarningThreshold(), recovery.getDelayedRequeueCriticalThreshold(),
                        "Tasks are being delayed because no assignable agent was available."),
                evaluate("dead-letter-volume", "deadLettered", totals.deadLettered(),
                        recovery.getDeadLetterWarningThreshold(), recovery.getDeadLetterCriticalThreshold(),
                        "Dispatch requests reached dead-letter and need operator recovery."),
                evaluate("scanner-failures", "delayedRequeueFailed", totals.delayedRequeueFailed(),
                        recovery.getScannerFailureWarningThreshold(), recovery.getScannerFailureCriticalThreshold(),
                        "Delayed dispatch recovery scanner failed while processing claimed tasks."),
                evaluate("recovery-exhausted", "recoveryExhausted", totals.recoveryExhausted(),
                        recovery.getRecoveryExhaustedWarningThreshold(), recovery.getRecoveryExhaustedCriticalThreshold(),
                        "Task-level delayed recovery exhausted its configured attempts.")
        );
    }

    private RecoveryAlertEvaluation evaluate(String code,
                                             String metric,
                                             long observed,
                                             int warningThreshold,
                                             int criticalThreshold,
                                             String message) {
        String severity = "OK";
        if (criticalThreshold > 0 && observed >= criticalThreshold) {
            severity = "CRITICAL";
        } else if (warningThreshold > 0 && observed >= warningThreshold) {
            severity = "WARNING";
        }
        return new RecoveryAlertEvaluation(code, severity, metric, observed, warningThreshold, criticalThreshold, message);
    }

    private RecoveryAlertPolicyView policyView(ObservabilityProperties.RecoveryMetrics recovery, Duration window, int limit) {
        return new RecoveryAlertPolicyView(
                recovery.isEnabled(),
                window.toString(),
                limit,
                recovery.getRuntimeFailureWarningThreshold(),
                recovery.getRuntimeFailureCriticalThreshold(),
                recovery.getDelayedRequeueWarningThreshold(),
                recovery.getDelayedRequeueCriticalThreshold(),
                recovery.getDeadLetterWarningThreshold(),
                recovery.getDeadLetterCriticalThreshold(),
                recovery.getScannerFailureWarningThreshold(),
                recovery.getScannerFailureCriticalThreshold(),
                recovery.getRecoveryExhaustedWarningThreshold(),
                recovery.getRecoveryExhaustedCriticalThreshold()
        );
    }

    private String worstStatus(List<RecoveryAlertEvaluation> alerts) {
        if (alerts.stream().anyMatch(alert -> "CRITICAL".equals(alert.severity()))) return "CRITICAL";
        if (alerts.stream().anyMatch(alert -> "WARNING".equals(alert.severity()))) return "WARNING";
        return "OK";
    }

    private List<DispatchAttemptHistoryRecord> recentCritical(List<DispatchAttemptHistoryRecord> records, int limit) {
        return records.stream()
                .filter(record -> CRITICAL_EVENT_TYPES.contains(record.getEventType()))
                .sorted(Comparator.comparing(DispatchAttemptHistoryRecord::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<RecoveryMetricBucket> buckets(Map<String, Long> counts, Map<String, OffsetDateTime> latest, int limit) {
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.getValue(), left.getValue());
                    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
                })
                .limit(limit)
                .map(entry -> new RecoveryMetricBucket(entry.getKey(), entry.getValue(), latest.get(entry.getKey())))
                .toList();
    }

    private Map<String, Long> countBy(List<DispatchAttemptHistoryRecord> records, java.util.function.Function<DispatchAttemptHistoryRecord, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DispatchAttemptHistoryRecord record : records) {
            String key = blankToNone(classifier.apply(record));
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
        }
        return counts;
    }

    private Map<String, OffsetDateTime> latestBy(List<DispatchAttemptHistoryRecord> records, java.util.function.Function<DispatchAttemptHistoryRecord, String> classifier) {
        Map<String, OffsetDateTime> latest = new HashMap<>();
        for (DispatchAttemptHistoryRecord record : records) {
            String key = blankToNone(classifier.apply(record));
            OffsetDateTime occurredAt = record.getOccurredAt();
            if (occurredAt == null) continue;
            OffsetDateTime current = latest.get(key);
            if (current == null || occurredAt.isAfter(current)) latest.put(key, occurredAt);
        }
        return latest;
    }

    private long count(Map<String, Long> counts, String key) {
        return counts.getOrDefault(key, 0L);
    }

    private Duration normalizeWindow(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) return Duration.ofMinutes(15);
        if (value.compareTo(Duration.ofMinutes(1)) < 0) return Duration.ofMinutes(1);
        if (value.compareTo(Duration.ofHours(24)) > 0) return Duration.ofHours(24);
        return value;
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 1000 : limit, 10000));
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }
}
