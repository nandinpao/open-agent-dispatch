package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.GovernedManagedAgentAssignmentRequest;
import com.opensocket.aievent.core.assignment.GovernedExternalProviderAssignmentRequest;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;

/** Public application boundary owned by the Task Orchestration module. */
public interface TaskOrchestrationFacade {
    TaskDecisionResult decide(Incident incident, NormalizedEvent event, DedupDecision dedup);
    AssignmentDecisionResult assignIfPossible(TaskRecord task);
    AssignmentDecisionResult assignTask(String taskId);
    /** Internal Stage 4 runtime path after capability authority has selected a managed provider. */
    AssignmentDecisionResult assignGovernedManagedAgent(GovernedManagedAgentAssignmentRequest request);
    /** Internal Stage 6/7 provider-neutral runtime path for MCP/A2A after server-side provider selection. */
    AssignmentDecisionResult assignGovernedExternalProvider(GovernedExternalProviderAssignmentRequest request);
    AssignmentDecisionResult recoverTaskDispatchNow(String taskId, String reason, OffsetDateTime now);
    boolean releaseCapacityReservation(String assignmentId);
    Optional<TaskRecord> findTask(String taskId);
    TaskRecord saveExecutionState(TaskRecord task);
    boolean transitionExecutionState(TaskExecutionStateTransition transition);

    TaskRecord timeoutTask(String taskId, String reason, OffsetDateTime now);
    TaskRecord cancelTask(String taskId, String reason, OffsetDateTime now);
    TaskRecord reassignTask(String taskId, String reason, OffsetDateTime now);
    TaskRecord holdTask(String taskId, String reason, OffsetDateTime now);
    TaskRecord resumeTask(String taskId, String reason, OffsetDateTime now);
    TaskRecord forceFailTask(String taskId, String reason, OffsetDateTime now);
    TaskDispatchRecoveryScanResult recoverDelayedDispatches(int limit, OffsetDateTime now);
    LifecycleScanResult processLifecycle(TaskLifecyclePolicy policy, OffsetDateTime now);
}
