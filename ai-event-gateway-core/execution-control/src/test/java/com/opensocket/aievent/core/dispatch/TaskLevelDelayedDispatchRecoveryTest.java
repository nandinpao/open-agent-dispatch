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
import com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryProperties;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryScanResult;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskLevelDelayedDispatchRecoveryTest {
    @Test
    void noCandidateShouldDeferTaskAndScannerShouldRecoverWhenAgentBecomesAvailable() {
        InMemoryAgentDirectoryRepository agentRepository = new InMemoryAgentDirectoryRepository();
        AgentDirectoryService agentDirectory = new AgentDirectoryService(agentRepository);

        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryTaskAssignmentRepository assignmentRepository = new InMemoryTaskAssignmentRepository();
        InMemoryRoutingDecisionRepository routingRepository = new InMemoryRoutingDecisionRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();

        RoutingProperties routingProperties = new RoutingProperties();
        routingProperties.setAssignmentEnabled(true);
        routingProperties.setMinimumScore(50);

        TaskDispatchRecoveryProperties recoveryProperties = new TaskDispatchRecoveryProperties();
        recoveryProperties.setEnabled(true);
        recoveryProperties.setInitialDelay(Duration.ofSeconds(5));
        recoveryProperties.setMaxDelay(Duration.ofSeconds(30));
        recoveryProperties.setClaimLease(Duration.ofSeconds(20));

        DispatchProperties dispatchProperties = new DispatchProperties();
        dispatchProperties.setRequestCreationEnabled(true);
        dispatchProperties.setReviewMode(DispatchReviewMode.AUTO_APPROVE);

        DispatchEligibilityService eligibility = new DispatchEligibilityService(agentDirectory, dispatchProperties);
        DispatchRequestService dispatchRequestService = new DispatchRequestService(dispatchRepository, eligibility, dispatchProperties);
        RoutingDecisionService routingDecisionService = new RoutingDecisionService(agentDirectory, routingRepository, routingProperties);
        TaskAssignmentService assignmentService = new TaskAssignmentService(
                routingDecisionService,
                assignmentRepository,
                agentDirectory,
                taskRepository,
                routingProperties,
                recoveryProperties,
                dispatchRequestService);
        DefaultTaskOrchestrationFacade taskFacade = new DefaultTaskOrchestrationFacade(
                null,
                assignmentService,
                taskRepository,
                assignmentRepository,
                routingRepository,
                recoveryProperties);

        TaskRecord task = task("task-p10-2");
        taskRepository.save(task);

        AssignmentDecisionResult initial = assignmentService.assignIfPossible(task);
        assertThat(initial.assignmentCreated()).isFalse();
        assertThat(initial.reason()).contains("nextDispatchAttemptAt");

        TaskRecord deferred = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(TaskStatus.RETRY_WAIT);
        assertThat(deferred.getDispatchAttemptCount()).isEqualTo(1);
        assertThat(deferred.getNextDispatchAttemptAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(deferred.getDispatchRetryReason()).contains("DISPATCH_DELAYED_NO_ELIGIBLE_AGENT");

        // Make the delayed task due and then bring an eligible runtime online.
        deferred.setNextDispatchAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        taskRepository.save(deferred);
        agentDirectory.register(agent("agent-p10-2", "gw-1", "session-p10-2"));

        TaskDispatchRecoveryScanResult scan = taskFacade.recoverDelayedDispatches(10, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(scan.getClaimed()).isEqualTo(1);
        assertThat(scan.getRecovered()).isEqualTo(1);
        TaskRecord recovered = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(recovered.getDispatchAttemptCount()).isEqualTo(1);
        assertThat(recovered.getNextDispatchAttemptAt()).isNull();
        assertThat(recovered.getDispatchRetryReason()).isNull();
        assertThat(recovered.getDispatchRecoveryClaimedBy()).isNull();
        assertThat(recovered.getDispatchRecoveryClaimUntil()).isNull();
        assertThat(assignmentRepository.findOpenByTaskId(task.getTaskId())).isPresent();
        assertThat(dispatchRepository.findByTaskId(task.getTaskId(), 10)).hasSize(1);
        assertThat(dispatchRepository.findByTaskId(task.getTaskId(), 10).getFirst().getStatus())
                .isEqualTo(DispatchRequestStatus.APPROVED);
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
        task.setIncidentId("incident-p10-2");
        task.setStatus(TaskStatus.QUEUED);
        task.setRoutingPolicy("CAPABILITY_FIRST");
        task.setRequiredCapabilities(List.of("ERP.ORDER.REPAIR"));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
