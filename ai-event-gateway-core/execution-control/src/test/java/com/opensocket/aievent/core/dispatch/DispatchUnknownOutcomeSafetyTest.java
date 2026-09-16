package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.InMemoryAgentDirectoryRepository;
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchUnknownOutcomeSafetyTest {

    @Test
    void responseLostMustHoldForReconciliationInsteadOfReassigningOrRetrying() {
        AgentDirectoryService agents = new AgentDirectoryService(new InMemoryAgentDirectoryRepository());
        agents.register(agent("agent-a"));
        agents.register(agent("agent-b"));
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryTaskAssignmentRepository assignments = new InMemoryTaskAssignmentRepository();
        InMemoryRoutingDecisionRepository routing = new InMemoryRoutingDecisionRepository();
        InMemoryDispatchRequestRepository dispatches = new InMemoryDispatchRequestRepository();

        RoutingProperties routingProperties = new RoutingProperties();
        routingProperties.setAssignmentEnabled(true);
        routingProperties.setMinimumScore(50);
        DispatchProperties properties = new DispatchProperties();
        properties.setRequestCreationEnabled(true);
        properties.setReviewMode(DispatchReviewMode.AUTO_APPROVE);
        properties.getRetry().setEnabled(true);
        properties.getRetry().setMaxAttempts(3);
        properties.getFailureRequeue().setEnabled(true);
        properties.getFailureRequeue().setMaxReassignments(2);

        DispatchRequestService requestService = new DispatchRequestService(
                dispatches, new DispatchEligibilityService(agents, properties), properties);
        TaskAssignmentService assignmentService = new TaskAssignmentService(
                new RoutingDecisionService(agents, routing, routingProperties),
                assignments, agents, tasks, routingProperties, requestService);
        DefaultTaskOrchestrationFacade taskFacade = new DefaultTaskOrchestrationFacade(
                null, assignmentService, tasks, assignments, routing);

        TaskRecord task = task("task-pc-s2-response-lost");
        tasks.save(task);
        AssignmentDecisionResult initial = assignmentService.assignIfPossible(task);
        assertThat(initial.dispatchRequestCreated()).isTrue();

        DispatchExecutionService execution = new DispatchExecutionService(
                dispatches,
                taskFacade,
                ignored -> GatewayDispatchResult.failure(0, "GATEWAY_DISPATCH_EXCEPTION", "connection reset after write"),
                properties,
                ExecutionMetricsPort.noop(),
                com.opensocket.aievent.core.outbox.ModuleEventPublisher.noop(),
                agents);

        DispatchExecutionResult result = execution.execute(initial.dispatchRequestId());
        DispatchRequest held = dispatches.findById(initial.dispatchRequestId()).orElseThrow();

        assertThat(result.isExecuted()).isFalse();
        assertThat(held.getStatus()).isEqualTo(DispatchRequestStatus.DELIVERY_UNKNOWN);
        assertThat(held.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.RECOVERY_PENDING);
        assertThat(held.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.RESPONSE_LOST);
        assertThat(held.getUncertainSince()).isNotNull();
        assertThat(held.getNextRetryAt()).isNull();
        assertThat(tasks.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.RECONCILING);
        assertThat(tasks.findById(task.getTaskId()).orElseThrow().getReassignmentCount()).isZero();
        assertThat(dispatches.findByTaskId(task.getTaskId(), 10)).hasSize(1);
    }

    private AgentSnapshot agent(String id) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(id);
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gw-1");
        agent.setAgentSessionId("session-" + id);
        agent.setStatus(AgentStatus.IDLE);
        agent.setHealthScore(100);
        agent.setMaxConcurrentTasks(2);
        agent.setAvailableSlots(2);
        agent.setCapabilities(List.of("ERP.ORDER.REPAIR"));
        agent.setLastHeartbeatAt(OffsetDateTime.now(ZoneOffset.UTC));
        return agent;
    }

    private TaskRecord task(String id) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(id);
        task.setIncidentId("incident-" + id);
        task.setStatus(TaskStatus.QUEUED);
        task.setRoutingPolicy("CAPABILITY_FIRST");
        task.setRequiredCapabilities(List.of("ERP.ORDER.REPAIR"));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
