package com.opensocket.aievent.core.task.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskDomainServiceTest {
    private InMemoryTaskRepository tasks;
    private InMemoryTaskRelationshipRepository relationships;
    private InMemoryTaskParticipantRepository participants;
    private InMemoryTaskStateHistoryRepository history;
    private TaskDomainService service;

    @BeforeEach
    void setUp() {
        tasks = new InMemoryTaskRepository();
        relationships = new InMemoryTaskRelationshipRepository();
        participants = new InMemoryTaskParticipantRepository();
        history = new InMemoryTaskStateHistoryRepository();
        service = new TaskDomainService(tasks, relationships, participants, history);
    }

    @Test
    void supportsMultipleReferencesAndReverseQueries() {
        tasks.save(task("tenant-a", "task-root", null, TaskStatus.READY));
        tasks.save(task("tenant-a", "task-101", null, TaskStatus.COMPLETED));
        tasks.save(task("tenant-a", "task-205", null, TaskStatus.COMPLETED));
        tasks.save(task("tenant-a", "task-330", null, TaskStatus.COMPLETED));

        List<TaskRelationship> created = service.addRelationships(
                "tenant-a", "task-root", List.of("task-101", "task-205", "task-330"),
                TaskRelationshipType.REFERENCES, TaskRelationshipDirection.DIRECTED,
                "ROOT_CAUSE_INPUT", "Use historical results for root-cause analysis.",
                TaskReferenceVisibility.VISIBLE, "operator-a", "reference-batch-1");

        assertEquals(3, created.size());
        assertEquals(3, service.relationships("tenant-a", "task-root", TaskRelationshipQueryDirection.OUTBOUND, 100).size());
        assertEquals(1, service.relationships("tenant-a", "task-205", TaskRelationshipQueryDirection.INBOUND, 100).size());
    }

    @Test
    void rejectsCrossTenantReferences() {
        tasks.save(task("tenant-a", "task-a", null, TaskStatus.READY));
        tasks.save(task("tenant-b", "task-b", null, TaskStatus.READY));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.addRelationships("tenant-a", "task-a", List.of("task-b"),
                        TaskRelationshipType.RELATED_TO, TaskRelationshipDirection.BIDIRECTIONAL,
                        null, null, TaskReferenceVisibility.OPAQUE, "operator-a", "cross-tenant-1"));
        assertTrue(error.getMessage().contains("Task not found in Tenant"));
    }

    @Test
    void returnsAnOrderedTaskChain() {
        tasks.save(task("tenant-a", "task-root", null, TaskStatus.READY));
        tasks.save(task("tenant-a", "task-child", "task-root", TaskStatus.RUNNING));
        tasks.save(task("tenant-a", "task-grandchild", "task-child", TaskStatus.WAITING_DEPENDENCY));

        TaskChainView chain = service.chain("tenant-a", "task-grandchild", 100);
        assertEquals("task-root", chain.rootTaskId());
        assertEquals(List.of(0, 1, 2), chain.nodes().stream().map(TaskChainNode::depth).toList());
    }

    @Test
    void enforcesOptimisticLockAndReplaysAnIdempotentTransition() {
        TaskRecord record = task("tenant-a", "task-1", null, TaskStatus.READY);
        tasks.save(record);

        TaskStateTransitionCommand command = new TaskStateTransitionCommand(
                "tenant-a", "task-1", 1L, TaskStatus.QUEUED,
                "TASK_QUEUED", "Task is ready for dispatch.", TaskActorType.USER,
                "operator-a", "correlation-1", "transition-1", OffsetDateTime.now(ZoneOffset.UTC));
        TaskRecord first = service.transition(command);
        TaskRecord replay = service.transition(command);

        assertEquals(TaskStatus.QUEUED, first.getStatus());
        assertEquals(first.getVersion(), replay.getVersion());
        assertEquals(1, service.stateHistory("tenant-a", "task-1", 100).size());

        TaskStateTransitionCommand stale = new TaskStateTransitionCommand(
                "tenant-a", "task-1", 1L, TaskStatus.ASSIGNED,
                "TASK_ASSIGNED", "Assignment created.", TaskActorType.USER,
                "operator-a", "correlation-2", "transition-2", OffsetDateTime.now(ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> service.transition(stale));
    }

    @Test
    void participantMutationIsNaturallyIdempotent() {
        tasks.save(task("tenant-a", "task-1", null, TaskStatus.READY));
        TaskParticipant first = service.addParticipant("tenant-a", "task-1",
                TaskParticipantType.DEPARTMENT, "MES", TaskParticipantRole.EXECUTOR,
                TaskParticipantVisibilityLevel.FULL, TaskParticipantOperationLevel.OPERATE,
                "operator-a", "participant-1");
        TaskParticipant replay = service.addParticipant("tenant-a", "task-1",
                TaskParticipantType.DEPARTMENT, "MES", TaskParticipantRole.EXECUTOR,
                TaskParticipantVisibilityLevel.FULL, TaskParticipantOperationLevel.OPERATE,
                "operator-a", "participant-1");
        assertEquals(first.getParticipantId(), replay.getParticipantId());
        assertEquals(1, service.participants("tenant-a", "task-1", 100).size());
    }

    private TaskRecord task(String tenantId, String taskId, String parentTaskId, TaskStatus status) {
        TaskRecord task = new TaskRecord();
        task.setTenantId(tenantId);
        task.setTaskId(taskId);
        task.setTaskKey(taskId);
        task.setTitle("Task " + taskId);
        task.setStatus(status);
        task.setParentTaskId(parentTaskId);
        task.setRootTaskId(parentTaskId == null ? taskId : null);
        task.setVersion(1L);
        task.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setUpdatedAt(task.getCreatedAt());
        return task;
    }
}
