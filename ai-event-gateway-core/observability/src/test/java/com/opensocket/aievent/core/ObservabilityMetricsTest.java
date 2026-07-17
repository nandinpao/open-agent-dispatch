package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.dispatch.DispatchExecutionResult;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.observability.CoreMetricsService;
import com.opensocket.aievent.core.observability.ObservabilityProperties;
import com.opensocket.aievent.core.task.TaskDecisionResult;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ObservabilityMetricsTest {
    @Test
    void recordsBusinessMetricsWithLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setSlowIntakeThreshold(Duration.ofMillis(1));
        CoreMetricsService metrics = new CoreMetricsService(registry, properties);

        NormalizedEvent event = new NormalizedEvent(
                "evt-1", "tenant-a", "MES", "EXTERNAL", "MES", "NO_TARGET_SYSTEM", "TW", "P1", "EQUIPMENT", "M-01",
                "ALARM", "E-001", "NO_REQUESTED_SKILL", "NO_HANDOFF_MODE", "NO_CORRELATION_ID", "NO_PARENT_TASK_ID", EventSeverity.CRITICAL, "equipment alarm", OffsetDateTime.now(ZoneOffset.UTC), Map.of());
        DedupState state = new DedupState("fp-1", "inc-1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), 31, EventSeverity.CRITICAL, "evt-1", "equipment alarm");
        Incident incident = new Incident();
        incident.setIncidentId("inc-1");
        incident.setStatus(IncidentStatus.ESCALATED);
        incident.setSeverity(EventSeverity.CRITICAL);
        TaskDecisionResult taskDecision = TaskDecisionResult.created(newTask(), "created", List.of(), null);

        metrics.recordIntake(event, new DedupDecision(true, state, "duplicate"), incident, DecisionType.DUPLICATE_AGGREGATED, taskDecision, Duration.ofMillis(5));

        assertThat(registry.find("aeg.core.events.intake.total").counter()).isNotNull();
        assertThat(registry.find("aeg.core.dedup.decisions.total").counter()).isNotNull();
        assertThat(registry.find("aeg.core.events.intake.slow.total").counter()).isNotNull();
    }

    @Test
    void recordsDispatchExecutionMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoreMetricsService metrics = new CoreMetricsService(registry, new ObservabilityProperties());
        DispatchExecutionResult result = new DispatchExecutionResult();
        result.setExecuted(false);
        result.setDispatchStatus("RETRY_WAITING");
        result.setGatewayStatus("HTTP_503");
        result.setTaskStatus("DISPATCHED");

        metrics.recordDispatchExecution(result, Duration.ofMillis(10));

        assertThat(registry.find("aeg.core.dispatch.executions.total").counter()).isNotNull();
        assertThat(registry.find("aeg.core.dispatch.execution.duration").timer()).isNotNull();
    }


    @Test
    void dispatchGatewayStatusMetricsShouldBucketHttpCodesToBoundCardinality() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoreMetricsService metrics = new CoreMetricsService(registry, new ObservabilityProperties());

        for (int code = 400; code < 500; code++) {
            DispatchExecutionResult result = new DispatchExecutionResult();
            result.setExecuted(false);
            result.setDispatchStatus("FAILED");
            result.setGatewayStatus("HTTP_" + code);
            result.setTaskStatus("DISPATCHED");
            metrics.recordDispatchExecution(result, Duration.ofMillis(1));
        }

        var meters = registry.find("aeg.core.dispatch.executions.total").meters();
        assertThat(meters).hasSize(1);
        assertThat(meters.stream().findFirst().orElseThrow().getId().getTag("gateway_status")).isEqualTo("http_4xx");
    }

    @Test
    void intakeMetricsShouldNotUsePerEventOrPerObjectIdentifiersAsTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoreMetricsService metrics = new CoreMetricsService(registry, new ObservabilityProperties());

        for (int index = 0; index < 100; index++) {
            NormalizedEvent event = new NormalizedEvent(
                    "evt-p2-" + index,
                    "tenant-" + index,
                    "MES",
                    "EXTERNAL",
                    "MES",
                    "NO_TARGET_SYSTEM",
                    "TW",
                    "P1",
                    "EQUIPMENT",
                    "machine-" + index,
                    "ALARM",
                    "E-" + index,
                    "NO_REQUESTED_SKILL",
                    "NO_HANDOFF_MODE",
                    "NO_CORRELATION_ID",
                    "NO_PARENT_TASK_ID",
                    EventSeverity.CRITICAL,
                    "equipment alarm " + index,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    Map.of("rawId", "raw-" + index));
            DedupState state = new DedupState(
                    "fp-" + index,
                    "inc-" + index,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    1,
                    EventSeverity.CRITICAL,
                    event.eventId(),
                    event.normalizedMessage());
            Incident incident = new Incident();
            incident.setIncidentId("inc-" + index);
            incident.setStatus(IncidentStatus.ESCALATED);
            incident.setSeverity(EventSeverity.CRITICAL);

            metrics.recordIntake(event, new DedupDecision(false, state, "new"), incident, DecisionType.INCIDENT_CREATED, null, Duration.ofMillis(1));
        }

        var intakeMeters = registry.find("aeg.core.events.intake.total").meters();
        assertThat(intakeMeters).hasSize(1);
        assertThat(intakeMeters.stream().findFirst().orElseThrow().getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain("event_id", "tenant_id", "object_id", "raw_id", "fingerprint", "incident_id");
    }

    private com.opensocket.aievent.core.task.TaskRecord newTask() {
        com.opensocket.aievent.core.task.TaskRecord task = new com.opensocket.aievent.core.task.TaskRecord();
        task.setTaskId("task-1");
        task.setTaskType(com.opensocket.aievent.core.task.TaskType.INCIDENT_RESPONSE);
        return task;
    }
}
