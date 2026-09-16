package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

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

class DispatchFailureSemanticsC0A3Test {

    @Test
    void infrastructureFailureShouldRetryInsteadOfDeadLetteringTask() throws Exception {
        Fixture f = fixture(DispatchExecutionSafetyDecision.retryInfrastructure("DB_TEMPORARY", "database unavailable"));

        DispatchExecutionResult result = f.execution.execute(f.initial.dispatchRequestId());
        DispatchRequest request = f.dispatches.findById(f.initial.dispatchRequestId()).orElseThrow();
        TaskRecord task = f.tasks.findById(f.taskId).orElseThrow();

        assertThat(result.isExecuted()).isFalse();
        assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.RETRY_WAITING);
        assertThat(request.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.FAILED_RETRYABLE);
        assertThat(request.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.INFRASTRUCTURE_RETRY);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RETRY_WAIT);
    }

    @Test
    void authorityLossShouldAbandonStaleDispatchAndReassignTask() throws Exception {
        Fixture f = fixture(DispatchExecutionSafetyDecision.reassignRequired("ASSIGNMENT_LEASE_EXPIRED", "lease expired"));

        DispatchExecutionResult result = f.execution.execute(f.initial.dispatchRequestId());
        DispatchRequest request = f.dispatches.findById(f.initial.dispatchRequestId()).orElseThrow();
        TaskRecord task = f.tasks.findById(f.taskId).orElseThrow();

        assertThat(result.isExecuted()).isFalse();
        assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.FAILED);
        assertThat(request.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.ABANDONED);
        assertThat(request.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.REASSIGN_REQUIRED);
        assertThat(task.getReassignmentCount()).isEqualTo(1);
        assertThat(task.getStatus()).isIn(TaskStatus.QUEUED, TaskStatus.ASSIGNED);
        assertThat(f.dispatches.findByTaskId(f.taskId, 10)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void policyBlockShouldHoldTaskWithoutDeadLetter() throws Exception {
        Fixture f = fixture(DispatchExecutionSafetyDecision.blockPolicy("FLOW_POLICY_BLOCK", "flow not authoritative"));

        f.execution.execute(f.initial.dispatchRequestId());
        DispatchRequest request = f.dispatches.findById(f.initial.dispatchRequestId()).orElseThrow();
        TaskRecord task = f.tasks.findById(f.taskId).orElseThrow();

        assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.FAILED);
        assertThat(request.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.BLOCKED);
        assertThat(request.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.POLICY_BLOCKED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(task.getStatus()).isNotEqualTo(TaskStatus.DEAD_LETTER);
    }

    @Test
    void securityBlockShouldHoldTaskWithoutDeadLetter() throws Exception {
        Fixture f = fixture(DispatchExecutionSafetyDecision.blockSecurity("AUTHORIZATION_REVOKED", "authorization revoked"));

        f.execution.execute(f.initial.dispatchRequestId());
        DispatchRequest request = f.dispatches.findById(f.initial.dispatchRequestId()).orElseThrow();
        TaskRecord task = f.tasks.findById(f.taskId).orElseThrow();

        assertThat(request.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.BLOCKED);
        assertThat(request.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.SECURITY_BLOCKED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.BLOCKED);
    }

    @Test
    void terminalFailureShouldRemainDeadLetter() throws Exception {
        Fixture f = fixture(DispatchExecutionSafetyDecision.terminal("AUTHORITY_CORRUPT", "canonical authority is inconsistent"));

        f.execution.execute(f.initial.dispatchRequestId());
        DispatchRequest request = f.dispatches.findById(f.initial.dispatchRequestId()).orElseThrow();
        TaskRecord task = f.tasks.findById(f.taskId).orElseThrow();

        assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.DEAD_LETTER);
        assertThat(request.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.DEAD_LETTER);
        assertThat(request.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.TERMINAL_FAILURE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
    }

    @Test
    void outboxStateMachineShouldExposeBlockedAndAbandonedAsTerminalWorkerOutcomes() {
        assertThat(DispatchOutboxStateMachine.canTransition(DispatchOutboxStatus.CLAIMED, DispatchOutboxStatus.ABANDONED)).isTrue();
        assertThat(DispatchOutboxStateMachine.canTransition(DispatchOutboxStatus.DISPATCHING, DispatchOutboxStatus.BLOCKED)).isTrue();
        assertThat(DispatchOutboxStateMachine.canTransition(DispatchOutboxStatus.BLOCKED, DispatchOutboxStatus.CLAIMED)).isFalse();
        assertThat(DispatchOutboxStateMachine.canTransition(DispatchOutboxStatus.ABANDONED, DispatchOutboxStatus.CLAIMED)).isFalse();
    }

    private Fixture fixture(DispatchExecutionSafetyDecision safetyDecision) throws Exception {
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

        String taskId = "task-c0a3-" + safetyDecision.disposition().name().toLowerCase();
        TaskRecord task = task(taskId);
        tasks.save(task);
        AssignmentDecisionResult initial = assignmentService.assignIfPossible(task);
        assertThat(initial.dispatchRequestCreated()).isTrue();

        DispatchExecutionService execution = new DispatchExecutionService(
                dispatches, taskFacade,
                ignored -> { throw new AssertionError("network dispatch must not be reached when the execution safety guard blocks"); },
                properties, ExecutionMetricsPort.noop(),
                com.opensocket.aievent.core.outbox.ModuleEventPublisher.noop(), agents);
        Field guards = DispatchExecutionService.class.getDeclaredField("executionSafetyGuards");
        guards.setAccessible(true);
        guards.set(execution, List.<DispatchExecutionSafetyGuard>of((request, now) -> safetyDecision));
        return new Fixture(execution, dispatches, tasks, initial, taskId);
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

    private record Fixture(
            DispatchExecutionService execution,
            InMemoryDispatchRequestRepository dispatches,
            InMemoryTaskRepository tasks,
            AssignmentDecisionResult initial,
            String taskId) {}
}
