package com.opensocket.aievent.core.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.InMemoryAgentDirectoryRepository;
import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;
import com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class TaskAssignmentCapacityBoundaryTest {
    @Test
    void assignmentShouldReserveAndReleaseAgentCapacityAcrossTaskBoundary() {
        AgentDirectoryService agentDirectory = new AgentDirectoryService(new InMemoryAgentDirectoryRepository());
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId("agent-1");
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gateway-1");
        agent.setAgentSessionId("session-1");
        agent.setSiteId("SITE-A");
        agent.setStatus(AgentStatus.IDLE);
        agent.setCapabilities(List.of("issue-analysis"));
        agent.setMaxConcurrentTasks(1);
        agentDirectory.register(agent);

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(0);
        properties.setMaxCandidates(10);
        properties.setUpdateTaskStatusOnAssignment(true);
        properties.setAssignmentLeaseTtl(Duration.ofMinutes(17));

        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryTaskAssignmentRepository assignments = new InMemoryTaskAssignmentRepository();
        RoutingDecisionService routing = new RoutingDecisionService(
                agentDirectory, new InMemoryRoutingDecisionRepository(), properties);
        TaskDispatchPort noDispatch = (assignment, task) -> DispatchDecisionResult.none("execution-control not under test");
        TaskAssignmentService service = new TaskAssignmentService(
                routing, assignments, agentDirectory, tasks, properties, noDispatch);

        TaskRecord first = task("task-1");
        TaskRecord second = task("task-2");
        tasks.save(first);
        tasks.save(second);

        AssignmentDecisionResult firstResult = service.assignIfPossible(first);
        assertThat(firstResult.assignmentCreated()).isTrue();
        assertThat(firstResult.selectedAgentId()).isEqualTo("agent-1");
        TaskAssignment firstAssignment = assignments.findById(firstResult.assignmentId()).orElseThrow();
        assertThat(firstAssignment.isCapacityReserved()).isTrue();
        assertThat(firstAssignment.getLeaseExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(16));
        assertThat(firstAssignment.getLeaseExpiresAt()).isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(18));
        assertThat(agentDirectory.findById("agent-1").orElseThrow().getReservedTaskCount()).isEqualTo(1);

        AssignmentDecisionResult blocked = service.assignIfPossible(second);
        assertThat(blocked.assignmentCreated()).isFalse();
        assertThat(blocked.selectedAgentId()).isNull();

        assertThat(service.releaseCapacityReservation(firstResult.assignmentId())).isTrue();
        assertThat(assignments.findById(firstResult.assignmentId()).orElseThrow().isCapacityReserved()).isFalse();
        assertThat(agentDirectory.findById("agent-1").orElseThrow().getReservedTaskCount()).isZero();

        AssignmentDecisionResult secondResult = service.assignIfPossible(second);
        assertThat(secondResult.assignmentCreated()).isTrue();
        assertThat(secondResult.selectedAgentId()).isEqualTo("agent-1");
    }

    private TaskRecord task(String taskId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setIncidentId("incident-" + taskId);
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setStatus(TaskStatus.QUEUED);
        task.setSiteId("SITE-A");
        task.setRoutingPolicy("CAPABILITY_FIRST");
        task.setRequiredCapabilities(List.of("issue-analysis"));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
