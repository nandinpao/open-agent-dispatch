package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.InMemoryAdapterActionRepository;
import com.opensocket.aievent.core.callback.InMemoryTaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.InMemoryIncidentRepository;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class RepositoryIdempotencyTest {
    @Test
    void activeIncidentShouldBeCreateOrReturnExistingByFingerprint() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident first = incident("inc-1", "fp-1");
        Incident second = incident("inc-2", "fp-1");

        assertThat(repository.saveNewOrGetActive(first).getIncidentId()).isEqualTo("inc-1");
        assertThat(repository.saveNewOrGetActive(second).getIncidentId()).isEqualTo("inc-1");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void openTaskShouldBeCreateOrReturnExistingByIncidentAndType() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskRecord first = task("task-1", "inc-1", TaskType.INCIDENT_RESPONSE);
        TaskRecord second = task("task-2", "inc-1", TaskType.INCIDENT_RESPONSE);

        assertThat(repository.saveNewOrGetOpen(first).getTaskId()).isEqualTo("task-1");
        assertThat(repository.saveNewOrGetOpen(second).getTaskId()).isEqualTo("task-1");
        assertThat(repository.findByIncidentId("inc-1", 10)).hasSize(1);
    }

    @Test
    void callbackReservationShouldRejectDuplicateCallbackId() {
        InMemoryTaskCallbackRepository repository = new InMemoryTaskCallbackRepository();
        TaskCallbackRecord first = callback("cb-1");
        TaskCallbackRecord second = callback("cb-1");

        assertThat(repository.tryReserve(first)).isTrue();
        assertThat(repository.tryReserve(second)).isFalse();
    }

    @Test
    void adapterActionShouldBeCreateOrReturnExistingByIdempotencyKey() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction first = action("act-1", "idem-1");
        AdapterAction second = action("act-2", "idem-1");

        assertThat(repository.saveNewOrGetByIdempotencyKey(first).getActionId()).isEqualTo("act-1");
        assertThat(repository.saveNewOrGetByIdempotencyKey(second).getActionId()).isEqualTo("act-1");
        assertThat(repository.recent(10)).hasSize(1);
    }

    @Test
    void concurrentOpenTaskShouldStillReturnSingleOpenTaskInMemory() throws Exception {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        int calls = 64;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TaskRecord>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return repository.saveNewOrGetOpen(task("task-" + index, "inc-concurrent", TaskType.INCIDENT_RESPONSE));
            }));
        }
        start.countDown();

        List<TaskRecord> results = new ArrayList<>();
        for (Future<TaskRecord> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        assertThat(results.stream().map(TaskRecord::getTaskId).distinct()).hasSize(1);
        assertThat(repository.findByIncidentId("inc-concurrent", 100)).hasSize(1);
    }

    private Incident incident(String id, String fingerprint) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Incident incident = new Incident();
        incident.setIncidentId(id);
        incident.setFingerprint(fingerprint);
        incident.setTenantId("tenant-a");
        incident.setSourceSystem("MES");
        incident.setSeverity(EventSeverity.HIGH);
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setFirstSeenAt(now);
        incident.setLastSeenAt(now);
        incident.setOccurrenceCount(1);
        return incident;
    }

    private TaskRecord task(String id, String incidentId, TaskType type) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(id);
        task.setIncidentId(incidentId);
        task.setTaskType(type);
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.HIGH);
        task.setTenantId("tenant-a");
        task.setRoutingPolicy("MANUAL_REVIEW");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private TaskCallbackRecord callback(String id) {
        TaskCallbackRecord record = new TaskCallbackRecord();
        record.setCallbackId(id);
        record.setCallbackType(TaskCallbackType.RESULT);
        record.setTaskId("task-1");
        record.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return record;
    }

    private AdapterAction action(String id, String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = new AdapterAction();
        action.setActionId(id);
        action.setIdempotencyKey(idempotencyKey);
        action.setAdapterName("mock");
        action.setAdapterType(AdapterType.MCP);
        action.setActionType(AdapterActionType.MCP_CONTEXT_FETCH);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        return action;
    }
}
