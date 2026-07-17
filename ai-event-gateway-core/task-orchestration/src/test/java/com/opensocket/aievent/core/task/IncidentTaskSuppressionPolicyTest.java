package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentStatus;

class IncidentTaskSuppressionPolicyTest {
    @Test
    void duplicateEventOnOpenIncidentShouldBeSuppressedBeforeThreshold() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskOrchestrationProperties properties = new TaskOrchestrationProperties();
        properties.setTaskCreationEnabled(true);
        properties.setTaskMinOccurrences(3);
        TaskDecisionService service = new TaskDecisionService(
                tasks,
                mock(IncidentFacade.class),
                properties,
                null,
                new IncidentTaskSuppressionPolicy());

        TaskDecisionResult result = service.decide(
                incident("inc-15a", IncidentStatus.ACTIVE),
                event(EventSeverity.HIGH),
                dedup(true, 2));

        assertThat(result.taskCreated()).isFalse();
        assertThat(result.taskSuppressed()).isTrue();
        assertThat(result.actions()).contains(
                DecisionAction.SUPPRESS_DUPLICATE_EVENT,
                DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK,
                DecisionAction.SUPPRESS_NEW_TASK);
        assertThat(tasks.search(new TaskQuery())).isEmpty();
    }

    @Test
    void openResponseTaskShouldSuppressAdditionalResponseTaskEvenAfterThreshold() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskRecord existing = task("task-existing", "inc-15a");
        tasks.save(existing);
        TaskOrchestrationProperties properties = new TaskOrchestrationProperties();
        properties.setTaskCreationEnabled(true);
        properties.setTaskMinOccurrences(3);
        TaskDecisionService service = new TaskDecisionService(
                tasks,
                mock(IncidentFacade.class),
                properties,
                null,
                new IncidentTaskSuppressionPolicy());

        TaskDecisionResult result = service.decide(
                incident("inc-15a", IncidentStatus.ACTIVE),
                event(EventSeverity.HIGH),
                dedup(true, 3));

        assertThat(result.taskCreated()).isFalse();
        assertThat(result.taskSuppressed()).isTrue();
        assertThat(result.taskId()).isNull();
        assertThat(result.actions()).contains(
                DecisionAction.SUPPRESS_DUPLICATE_TASK,
                DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK,
                DecisionAction.SUPPRESS_NEW_TASK);
        assertThat(tasks.findByIncidentId("inc-15a", 10)).hasSize(1);
    }

    @Test
    void thresholdReachedWithoutOpenTaskShouldCreateOneResponseTask() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskOrchestrationProperties properties = new TaskOrchestrationProperties();
        properties.setTaskCreationEnabled(true);
        properties.setTaskMinOccurrences(3);
        TaskDecisionService service = new TaskDecisionService(
                tasks,
                mock(IncidentFacade.class),
                properties,
                null,
                new IncidentTaskSuppressionPolicy());

        TaskDecisionResult result = service.decide(
                incident("inc-15a", IncidentStatus.ACTIVE),
                event(EventSeverity.HIGH),
                dedup(true, 3));

        assertThat(result.taskCreated()).isTrue();
        assertThat(result.taskSuppressed()).isFalse();
        assertThat(result.actions()).contains(DecisionAction.CREATE_TASK);
        assertThat(tasks.findByIncidentId("inc-15a", 10)).hasSize(1);
    }

    private Incident incident(String id, IncidentStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Incident incident = new Incident();
        incident.setIncidentId(id);
        incident.setFingerprint("fp-15a");
        incident.setTenantId("tenant-a");
        incident.setSourceSystem("MES");
        incident.setSiteId("TNN");
        incident.setPlantId("FAB-01");
        incident.setObjectType("EQUIPMENT");
        incident.setObjectId("EQP-1001");
        incident.setEventType("EQUIPMENT_ALARM");
        incident.setErrorCode("TEMP_HIGH");
        incident.setSeverity(EventSeverity.HIGH);
        incident.setStatus(status);
        incident.setFirstSeenAt(now.minusMinutes(3));
        incident.setLastSeenAt(now);
        incident.setOccurrenceCount(2);
        return incident;
    }

    private NormalizedEvent event(EventSeverity severity) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new NormalizedEvent(
                "evt-15a",
                "TENANT-A",
                "MES",
                "EXTERNAL",
                "MES",
                "NO_TARGET_SYSTEM",
                "TNN",
                "FAB-01",
                "EQUIPMENT",
                "EQP-1001",
                "EQUIPMENT_ALARM",
                "TEMP_HIGH",
                "NO_REQUESTED_SKILL",
                "NO_HANDOFF_MODE",
                "NO_CORRELATION_ID",
                "NO_PARENT_TASK_ID",
                severity,
                "chamber temperature over threshold",
                now,
                java.util.Map.of());
    }

    private DedupDecision dedup(boolean duplicate, long occurrenceCount) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DedupState state = new DedupState(
                "fp-15a",
                "inc-15a",
                now.minusMinutes(3),
                now,
                occurrenceCount,
                EventSeverity.HIGH,
                "evt-15a",
                "chamber temperature over threshold");
        return new DedupDecision(duplicate, state, duplicate ? "duplicate aggregated" : "new fingerprint");
    }

    private TaskRecord task(String taskId, String incidentId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setIncidentId(incidentId);
        task.setSourceEventId("evt-existing");
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.HIGH);
        task.setTenantId("tenant-a");
        task.setSiteId("TNN");
        task.setPlantId("FAB-01");
        task.setObjectType("EQUIPMENT");
        task.setObjectId("EQP-1001");
        task.setEventType("EQUIPMENT_ALARM");
        task.setErrorCode("TEMP_HIGH");
        task.setRequiredCapabilities(List.of("incident-analysis"));
        task.setCreatedAt(now.minusMinutes(1));
        task.setUpdatedAt(now.minusMinutes(1));
        return task;
    }
}
