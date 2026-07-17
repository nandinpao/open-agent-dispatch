package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.InMemoryDispatchRequestRepository;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.DefaultIncidentFacade;
import com.opensocket.aievent.core.incident.InMemoryIncidentRepository;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentManager;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.lifecycle.IncidentLifecycleService;
import com.opensocket.aievent.core.lifecycle.LifecycleProperties;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository;
import com.opensocket.aievent.core.summary.InMemoryIncidentOccurrenceSummaryRepository;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class IncidentTaskLifecycleTest {
    @Test
    void staleActiveIncidentShouldBeAutoResolvedAndCanBeReopened() {
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        DefaultIncidentFacade facade = new DefaultIncidentFacade(new IncidentManager(incidents), incidents, new InMemoryIncidentOccurrenceSummaryRepository());
        LifecycleProperties properties = new LifecycleProperties();
        properties.getIncident().setInactiveThreshold(Duration.ofHours(12));
        IncidentLifecycleService service = new IncidentLifecycleService(facade, properties);
        Incident incident = incident("inc-stale", OffsetDateTime.now(ZoneOffset.UTC).minusHours(13));
        incidents.save(incident);
        LifecycleScanResult result = service.autoResolveStaleIncidents();
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(incidents.findById("inc-stale").orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        Incident reopened = service.reopen("inc-stale", "same error happened again");
        assertThat(reopened.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(reopened.getReopenCount()).isEqualTo(1);
    }

    @Test
    void taskLifecycleUsesOwnerFacadeAndCancelsDispatchThroughPort() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryTaskAssignmentRepository assignments = new InMemoryTaskAssignmentRepository();
        InMemoryDispatchRequestRepository dispatches = new InMemoryDispatchRequestRepository();
        var facade = new DefaultTaskOrchestrationFacade(
                null, mock(TaskAssignmentService.class), tasks, assignments,
                new InMemoryRoutingDecisionRepository(),
                (assignmentId, reason, now) -> dispatches.findOpenByAssignmentId(assignmentId).map(d -> {d.setStatus(DispatchRequestStatus.CANCELLED);d.setReason(reason);d.setUpdatedAt(now);dispatches.save(d);return true;}).orElse(false),
                ModuleEventPublisher.noop());
        LifecycleProperties properties = new LifecycleProperties();
        properties.getTask().setAssignedTimeout(Duration.ofMinutes(10));
        properties.getTask().setAutoReassignEnabled(true);
        properties.getTask().setMaxReassignments(1);
        TaskLifecycleService service = new TaskLifecycleService(facade, properties);
        OffsetDateTime stale = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(11);
        TaskRecord task = task("task-assigned", TaskStatus.ASSIGNED, stale); tasks.save(task);
        assignments.save(assignment("assign-1", task.getTaskId(), task.getIncidentId(), stale));
        dispatches.save(dispatch("dispatch-1", "assign-1", task.getTaskId(), task.getIncidentId(), stale));
        LifecycleScanResult result = service.processTimeoutsAndReassignments();
        assertThat(result.getReassigned()).isEqualTo(1);
        assertThat(tasks.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(assignments.findById("assign-1").orElseThrow().getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(dispatches.findById("dispatch-1").orElseThrow().getStatus()).isEqualTo(DispatchRequestStatus.CANCELLED);
    }

    private Incident incident(String id, OffsetDateTime lastSeenAt) {Incident i=new Incident();i.setIncidentId(id);i.setFingerprint("fp-lifecycle");i.setTenantId("tenant-a");i.setSourceSystem("MES");i.setSeverity(EventSeverity.HIGH);i.setStatus(IncidentStatus.ACTIVE);i.setFirstSeenAt(lastSeenAt.minusMinutes(5));i.setLastSeenAt(lastSeenAt);i.setOccurrenceCount(1);return i;}
    private TaskRecord task(String id, TaskStatus status, OffsetDateTime updatedAt) {TaskRecord t=new TaskRecord();t.setTaskId(id);t.setIncidentId("inc-task-lifecycle");t.setTaskType(TaskType.INCIDENT_RESPONSE);t.setStatus(status);t.setPriority(TaskPriority.HIGH);t.setTenantId("tenant-a");t.setRoutingPolicy("MANUAL_REVIEW");t.setCreatedAt(updatedAt.minusMinutes(1));t.setUpdatedAt(updatedAt);return t;}
    private TaskAssignment assignment(String id,String taskId,String incidentId,OffsetDateTime at){TaskAssignment a=new TaskAssignment();a.setAssignmentId(id);a.setTaskId(taskId);a.setIncidentId(incidentId);a.setAgentId("agent-a");a.setStatus(AssignmentStatus.ASSIGNED);a.setCreatedAt(at);a.setUpdatedAt(at);return a;}
    private DispatchRequest dispatch(String id,String assignmentId,String taskId,String incidentId,OffsetDateTime at){DispatchRequest d=new DispatchRequest();d.setDispatchRequestId(id);d.setAssignmentId(assignmentId);d.setTaskId(taskId);d.setIncidentId(incidentId);d.setStatus(DispatchRequestStatus.DISPATCHED);d.setCreatedAt(at);d.setUpdatedAt(at);return d;}
}
