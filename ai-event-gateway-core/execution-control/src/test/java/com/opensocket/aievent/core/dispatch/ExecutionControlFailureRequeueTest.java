package com.opensocket.aievent.core.dispatch;

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
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class ExecutionControlFailureRequeueTest {
    @Test
    void runtimeDeliveryFailureShouldBackoffFailedAgentAndRequeueSameTaskToAnotherAgent() {
        InMemoryAgentDirectoryRepository agentRepository = new InMemoryAgentDirectoryRepository();
        AgentDirectoryService agentDirectory = new AgentDirectoryService(agentRepository);
        agentDirectory.register(agent("agent-a", "gw-1", "session-a"));
        agentDirectory.register(agent("agent-b", "gw-1", "session-b"));

        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryTaskAssignmentRepository assignmentRepository = new InMemoryTaskAssignmentRepository();
        InMemoryRoutingDecisionRepository routingRepository = new InMemoryRoutingDecisionRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();

        RoutingProperties routingProperties = new RoutingProperties();
        routingProperties.setAssignmentEnabled(true);
        routingProperties.setMinimumScore(50);

        DispatchProperties dispatchProperties = new DispatchProperties();
        dispatchProperties.setRequestCreationEnabled(true);
        dispatchProperties.setReviewMode(DispatchReviewMode.AUTO_APPROVE);
        dispatchProperties.getFailureRequeue().setEnabled(true);
        dispatchProperties.getFailureRequeue().setMaxReassignments(2);
        dispatchProperties.getFailureRequeue().setRuntimeInitialBackoff(Duration.ofMinutes(1));
        dispatchProperties.getFailureRequeue().setRuntimeMaxBackoff(Duration.ofMinutes(5));

        DispatchEligibilityService eligibility = new DispatchEligibilityService(agentDirectory, dispatchProperties);
        DispatchRequestService dispatchRequestService = new DispatchRequestService(dispatchRepository, eligibility, dispatchProperties);
        RoutingDecisionService routingDecisionService = new RoutingDecisionService(agentDirectory, routingRepository, routingProperties);
        TaskAssignmentService assignmentService = new TaskAssignmentService(
                routingDecisionService,
                assignmentRepository,
                agentDirectory,
                taskRepository,
                routingProperties,
                dispatchRequestService);
        DefaultTaskOrchestrationFacade taskFacade = new DefaultTaskOrchestrationFacade(
                null,
                assignmentService,
                taskRepository,
                assignmentRepository,
                routingRepository);

        TaskRecord task = task("task-p10-1");
        taskRepository.save(task);
        AssignmentDecisionResult initial = assignmentService.assignIfPossible(task);
        assertThat(initial.assignmentCreated()).isTrue();
        assertThat(initial.dispatchRequestCreated()).isTrue();
        String initiallySelectedAgentId = initial.selectedAgentId();
        assertThat(initiallySelectedAgentId).isIn("agent-a", "agent-b");
        String expectedReplacementAgentId = initiallySelectedAgentId.equals("agent-a") ? "agent-b" : "agent-a";

        NettyDispatchPort netty = ignored -> GatewayDispatchResult.failure(
                503,
                "AGENT_NOT_CONNECTED",
                "agent socket is gone");
        DispatchExecutionService executionService = new DispatchExecutionService(
                dispatchRepository,
                taskFacade,
                netty,
                dispatchProperties,
                ExecutionMetricsPort.noop(),
                com.opensocket.aievent.core.outbox.ModuleEventPublisher.noop(),
                agentDirectory);

        DispatchExecutionResult result = executionService.execute(initial.dispatchRequestId());

        assertThat(result.isExecuted()).isFalse();
        assertThat(dispatchRepository.findById(initial.dispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.FAILED);
        assertThat(assignmentRepository.findById(initial.assignmentId()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.CANCELLED);

        AgentSnapshot failedAgent = agentDirectory.findById(initiallySelectedAgentId).orElseThrow();
        assertThat(failedAgent.getRuntimeFailureCount()).isEqualTo(1);
        assertThat(failedAgent.getRuntimeBackoffUntil()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(failedAgent.isAssignable()).isFalse();

        TaskRecord requeuedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(requeuedTask.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(requeuedTask.getReassignmentCount()).isEqualTo(1);
        assertThat(requeuedTask.getStatus()).isEqualTo(TaskStatus.ASSIGNED);

        List<TaskAssignment> assignments = assignmentRepository.findByTaskId(task.getTaskId(), 10);
        assertThat(assignments).hasSize(2);
        TaskAssignment replacement = assignmentRepository.findOpenByTaskId(task.getTaskId()).orElseThrow();
        assertThat(replacement.getAgentId()).isEqualTo(expectedReplacementAgentId);
        assertThat(replacement.getAssignmentId()).isNotEqualTo(initial.assignmentId());

        List<DispatchRequest> dispatches = dispatchRepository.findByTaskId(task.getTaskId(), 10);
        assertThat(dispatches).hasSize(2);
        assertThat(dispatches.stream()
                .filter(request -> request.getAssignmentId().equals(replacement.getAssignmentId()))
                .findFirst()
                .orElseThrow()
                .getStatus()).isEqualTo(DispatchRequestStatus.APPROVED);
    }

    private AgentSnapshot agent(String agentId, String gatewayNodeId, String sessionId) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(agentId);
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId(gatewayNodeId);
        agent.setAgentSessionId(sessionId);
        agent.setStatus(AgentStatus.IDLE);
        agent.setHealthScore(100);
        agent.setMaxConcurrentTasks(1);
        agent.setAvailableSlots(1);
        agent.setCapabilities(List.of("ERP.ORDER.REPAIR"));
        agent.setLastHeartbeatAt(OffsetDateTime.now(ZoneOffset.UTC));
        return agent;
    }

    private TaskRecord task(String taskId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setIncidentId("incident-p10-1");
        task.setStatus(TaskStatus.QUEUED);
        task.setRoutingPolicy("CAPABILITY_FIRST");
        task.setRequiredCapabilities(List.of("ERP.ORDER.REPAIR"));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
