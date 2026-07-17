package com.opensocket.aievent.core.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.InMemoryDispatchAttemptHistoryRepository;

import tools.jackson.databind.ObjectMapper;

class RecoveryOperationMetricsServiceTest {
    @Test
    void shouldAggregateRecoveryEventsAndEvaluateAlerts() {
        InMemoryDispatchAttemptHistoryRepository repository = new InMemoryDispatchAttemptHistoryRepository();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.append(record("1", DispatchAttemptHistoryService.EVENT_RUNTIME_DELIVERY_FAILED, "agent-a", now.minusMinutes(1)));
        repository.append(record("2", DispatchAttemptHistoryService.EVENT_DEAD_LETTERED, "agent-a", now.minusMinutes(1)));
        repository.append(record("3", DispatchAttemptHistoryService.EVENT_DELAYED_REQUEUE_SCHEDULED, null, now.minusMinutes(2)));

        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getRecoveryMetrics().setRuntimeFailureWarningThreshold(1);
        properties.getRecoveryMetrics().setRuntimeFailureCriticalThreshold(5);
        properties.getRecoveryMetrics().setDeadLetterWarningThreshold(1);
        properties.getRecoveryMetrics().setDeadLetterCriticalThreshold(2);

        DispatchAttemptHistoryService historyService = new DispatchAttemptHistoryService(repository, new ObjectMapper());
        RecoveryOperationMetricsSnapshot snapshot = new RecoveryOperationMetricsService(historyService, properties)
                .snapshot(Duration.ofMinutes(15), 100);

        assertThat(snapshot.status()).isEqualTo("WARNING");
        assertThat(snapshot.totals().runtimeDeliveryFailed()).isEqualTo(1);
        assertThat(snapshot.totals().deadLettered()).isEqualTo(1);
        assertThat(snapshot.totals().delayedRequeueScheduled()).isEqualTo(1);
        assertThat(snapshot.byAgent()).anySatisfy(bucket -> {
            assertThat(bucket.key()).isEqualTo("agent-a");
            assertThat(bucket.count()).isEqualTo(2);
        });
        assertThat(snapshot.recentCriticalEvents()).hasSize(2);
        assertThat(snapshot.alerts()).anySatisfy(alert -> {
            assertThat(alert.code()).isEqualTo("dead-letter-volume");
            assertThat(alert.severity()).isEqualTo("WARNING");
        });
    }

    private DispatchAttemptHistoryRecord record(String id, String eventType, String agentId, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = new DispatchAttemptHistoryRecord();
        record.setHistoryId("h-" + id);
        record.setTaskId("task-" + id);
        record.setAgentId(agentId);
        record.setEventType(eventType);
        record.setStatus("FAILED");
        record.setOccurredAt(occurredAt);
        record.setCreatedAt(occurredAt);
        record.setPayloadJson("{}");
        return record;
    }
}
