package com.opensocket.aievent.core.task;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.GovernedManagedAgentAssignmentRequest;
import com.opensocket.aievent.core.assignment.GovernedExternalProviderAssignmentRequest;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptHistoryPort;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionRepository;

@Service
public class DefaultTaskOrchestrationFacade implements TaskOrchestrationFacade, TaskOperationalQuery {
    private static final Logger log = LoggerFactory.getLogger(DefaultTaskOrchestrationFacade.class);
    private final TaskDecisionService taskDecisionService;
    private final TaskAssignmentService taskAssignmentService;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final TaskDispatchRecoveryProperties dispatchRecoveryProperties;

    @Autowired(required = false)
    private TaskExecutionLifecyclePort executionLifecyclePort = TaskExecutionLifecyclePort.noop();

    @Autowired(required = false)
    private ModuleEventPublisher eventPublisher = ModuleEventPublisher.noop();

    @Autowired(required = false)
    private TaskDispatchAttemptHistoryPort attemptHistoryPort = TaskDispatchAttemptHistoryPort.noop();

    @Autowired(required = false)
    private TaskTenantExecutionPort tenantExecutionPort = TaskTenantExecutionPort.direct();


    @Autowired
    public DefaultTaskOrchestrationFacade(TaskDecisionService taskDecisionService,
                                          TaskAssignmentService taskAssignmentService,
                                          TaskRepository taskRepository,
                                          TaskAssignmentRepository assignmentRepository,
                                          RoutingDecisionRepository routingDecisionRepository,
                                          TaskDispatchRecoveryProperties dispatchRecoveryProperties) {
        this.taskDecisionService = taskDecisionService;
        this.taskAssignmentService = taskAssignmentService;
        this.taskRepository = taskRepository;
        this.assignmentRepository = assignmentRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.dispatchRecoveryProperties = dispatchRecoveryProperties == null ? new TaskDispatchRecoveryProperties() : dispatchRecoveryProperties;
    }

    /** Compatibility constructor for focused unit tests. */
    public DefaultTaskOrchestrationFacade(TaskDecisionService taskDecisionService,
                                          TaskAssignmentService taskAssignmentService,
                                          TaskRepository taskRepository,
                                          TaskAssignmentRepository assignmentRepository,
                                          RoutingDecisionRepository routingDecisionRepository) {
        this(taskDecisionService, taskAssignmentService, taskRepository, assignmentRepository, routingDecisionRepository,
                new TaskDispatchRecoveryProperties());
    }

    /** Compatibility constructor for lifecycle-focused unit tests. */
    public DefaultTaskOrchestrationFacade(TaskDecisionService taskDecisionService,
                                          TaskAssignmentService taskAssignmentService,
                                          TaskRepository taskRepository,
                                          TaskAssignmentRepository assignmentRepository,
                                          RoutingDecisionRepository routingDecisionRepository,
                                          TaskExecutionLifecyclePort executionLifecyclePort,
                                          ModuleEventPublisher eventPublisher) {
        this(taskDecisionService, taskAssignmentService, taskRepository, assignmentRepository, routingDecisionRepository);
        this.executionLifecyclePort = executionLifecyclePort == null ? TaskExecutionLifecyclePort.noop() : executionLifecyclePort;
        this.eventPublisher = eventPublisher == null ? ModuleEventPublisher.noop() : eventPublisher;
    }


    /** Compatibility constructor for focused unit tests. */
    public DefaultTaskOrchestrationFacade(TaskDecisionService taskDecisionService,
                                          TaskAssignmentService taskAssignmentService,
                                          TaskRepository taskRepository) {
        this(taskDecisionService, taskAssignmentService, taskRepository,
                new com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository(),
                new com.opensocket.aievent.core.routing.InMemoryRoutingDecisionRepository());
    }

    @Override public TaskDecisionResult decide(Incident incident, NormalizedEvent event, DedupDecision dedup) { return taskDecisionService.decide(incident, event, dedup); }
    @Override public AssignmentDecisionResult assignIfPossible(TaskRecord task) { return assignDirect(task); }
    @Override public AssignmentDecisionResult assignTask(String taskId) { return assignDirect(requireTask(taskId)); }
    @Override public AssignmentDecisionResult assignGovernedManagedAgent(GovernedManagedAgentAssignmentRequest request) {
        if (request == null || request.taskId() == null || request.taskId().isBlank()) {
            return AssignmentDecisionResult.none("Governed managed-agent assignment request is required");
        }
        return taskAssignmentService.assignGovernedManagedAgent(requireTask(request.taskId()), request);
    }

    @Override public AssignmentDecisionResult assignGovernedExternalProvider(GovernedExternalProviderAssignmentRequest request) {
        if (request == null || request.taskId() == null || request.taskId().isBlank()) {
            return AssignmentDecisionResult.none("Governed external-provider assignment request is required");
        }
        return taskAssignmentService.assignGovernedExternalProvider(requireTask(request.taskId()), request);
    }

    @Override
    @Transactional
    public AssignmentDecisionResult recoverTaskDispatchNow(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new IllegalStateException("Terminal task cannot be recovered immediately: " + taskId + " status=" + task.getStatus());
        }
        if (task.getStatus()==TaskStatus.WAITING_HUMAN || task.getStatus()==TaskStatus.BLOCKED) {
            throw new IllegalStateException("Held/blocked task cannot be recovered until the incident control is released: " + taskId);
        }
        OffsetDateTime at = effectiveNow(now);
        taskRepository.clearDispatchDelay(taskId, at, firstNonBlank(reason, "Operator requested immediate dispatch recovery"));
        return assignDirect(requireTask(taskId));
    }
    @Override
    public boolean releaseCapacityReservation(String assignmentId) {
        return taskAssignmentService != null
                && taskAssignmentService.releaseCapacityReservation(assignmentId);
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<TaskRecord> findTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskRecord> findTask(String tenantId, String taskId) {
        return taskRepository.findByTenantAndId(tenantId, taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> findTaskFamily(String tenantId, String rootTaskId, int limit) {
        if (tenantId == null || tenantId.isBlank() || rootTaskId == null || rootTaskId.isBlank()) return List.of();
        return taskRepository.findByRootTaskId(tenantId, rootTaskId, Math.max(1, Math.min(limit, 1000)));
    }
    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> findTaskChildren(String tenantId, String parentTaskId, int limit) {
        if (tenantId == null || tenantId.isBlank() || parentTaskId == null || parentTaskId.isBlank()) return List.of();
        return taskRepository.findByParentTaskId(tenantId, parentTaskId, Math.max(1, Math.min(limit, 1000)));
    }
    @Override public TaskRecord saveExecutionState(TaskRecord task) { if(task==null||task.getTaskId()==null||task.getTaskId().isBlank())throw new IllegalArgumentException("task with taskId is required"); return taskRepository.save(task); }
    @Override public boolean transitionExecutionState(TaskExecutionStateTransition transition) { return taskRepository.transitionExecutionState(transition); }

    @Override
    @Transactional
    public TaskRecord timeoutTask(String taskId, String reason, OffsetDateTime now) {
        return markTimedOut(requireTask(taskId), firstNonBlank(reason, "Manually timed out"), effectiveNow(now));
    }

    @Override
    @Transactional
    public TaskRecord cancelTask(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task=requireTask(taskId); if(isTerminal(task.getStatus())) return task; OffsetDateTime at=effectiveNow(now);
        cancelOpenAssignmentAndDispatch(task, firstNonBlank(reason,"Manually cancelled"),at);
        task.setStatus(TaskStatus.CANCELLED); task.setTerminalAt(at); task.setUpdatedAt(at); task.setLifecycleReason(firstNonBlank(reason,"Manually cancelled"));
        TaskRecord saved=taskRepository.save(task); publishTerminal(saved,null,null,null,at); return saved;
    }

    @Override
    @Transactional
    public TaskRecord reassignTask(String taskId, String reason, OffsetDateTime now) {
        return reassignInternal(requireTask(taskId), firstNonBlank(reason,"Manually reassigned"), effectiveNow(now));
    }

    @Override
    @Transactional
    public TaskRecord holdTask(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task=requireTask(taskId); if(isTerminal(task.getStatus())) return task; OffsetDateTime at=effectiveNow(now);
        cancelOpenAssignmentAndDispatch(task,firstNonBlank(reason,"Held by Security Incident control"),at);
        task.setStatus(TaskStatus.WAITING_HUMAN); task.setUpdatedAt(at); task.setLifecycleReason(firstNonBlank(reason,"Held by Security Incident control"));
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public TaskRecord resumeTask(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task=requireTask(taskId); if(isTerminal(task.getStatus())) return task;
        if(task.getStatus()!=TaskStatus.WAITING_HUMAN && task.getStatus()!=TaskStatus.BLOCKED) return task;
        OffsetDateTime at=effectiveNow(now); task.setStatus(TaskStatus.QUEUED); task.setUpdatedAt(at); task.setLifecycleReason(firstNonBlank(reason,"Released from Security Incident hold"));
        TaskRecord saved=taskRepository.save(task); assignDirect(saved); return saved;
    }

    @Override
    @Transactional
    public TaskRecord forceFailTask(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task=requireTask(taskId); if(isTerminal(task.getStatus())) return task; OffsetDateTime at=effectiveNow(now);
        cancelOpenAssignmentAndDispatch(task,firstNonBlank(reason,"Force-failed by Security Incident operator"),at);
        task.setStatus(TaskStatus.FAILED); task.setTerminalAt(at); task.setUpdatedAt(at); task.setLifecycleReason(firstNonBlank(reason,"Force-failed by Security Incident operator"));
        TaskRecord saved=taskRepository.save(task); publishTerminal(saved,null,null,null,at); return saved;
    }

    @Override
    @Transactional
    public TaskDispatchRecoveryScanResult recoverDelayedDispatches(int limit, OffsetDateTime now) {
        if (taskAssignmentService == null || !dispatchRecoveryProperties.isEnabled()) {
            return TaskDispatchRecoveryScanResult.disabled("Task dispatch recovery is disabled");
        }
        OffsetDateTime at = effectiveNow(now);
        OffsetDateTime claimUntil = at.plus(dispatchRecoveryProperties.getClaimLease());
        List<TaskRecord> claimed;
        try {
            claimed = taskRepository.claimDispatchRecoveryDue(
                    dispatchRecoveryProperties.getWorkerId(),
                    at,
                    claimUntil,
                    Math.max(1, Math.min(limit, dispatchRecoveryProperties.getMaxBatchSize())));
        } catch (RuntimeException ex) {
            if (isMissingDispatchRecoverySchema(ex)) {
                return TaskDispatchRecoveryScanResult.disabled(
                        "Task dispatch recovery schema is not ready. Run Flyway migration V35/V38 or apply the task delayed dispatch recovery columns before enabling the scanner: "
                                + rootMessage(ex));
            }
            throw ex;
        }
        int recovered = 0, deferred = 0, skipped = 0, failed = 0;
        for (TaskRecord task : claimed) {
            if (task == null) {
                skipped++;
                continue;
            }
            try {
                attemptHistoryPort.recordRecoveryClaimed(task, firstNonBlank(task.getDispatchRecoveryClaimedBy(), dispatchRecoveryProperties.getWorkerId()), task.getDispatchRecoveryClaimUntil() == null ? claimUntil : task.getDispatchRecoveryClaimUntil(), at);
                log.info("task_dispatch_recovery_claimed taskId={} status={} routingPath={} matchedFlowId={} matchedRuleId={} requestedSkill={} nextDispatchAttemptAt={} retryReason={}",
                        task.getTaskId(), task.getStatus(), task.getRoutingPath(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(),
                        task.getNextDispatchAttemptAt(), task.getDispatchRetryReason());
                if (isTerminal(task.getStatus())) {
                    skipped++;
                    log.info("task_dispatch_recovery_skipped taskId={} reason=TERMINAL status={}", task.getTaskId(), task.getStatus());
                    continue;
                }
                AssignmentDecisionResult assignment = assignDirect(task);
                if (assignment.assignmentCreated() || assignment.dispatchRequestCreated()) {
                    recovered++;
                    log.info("task_dispatch_recovery_recovered taskId={} assignmentCreated={} assignmentId={} selectedAgentId={} dispatchRequestCreated={} dispatchRequestId={} assignmentStatus={} reason={}",
                            task.getTaskId(), assignment.assignmentCreated(), assignment.assignmentId(), assignment.selectedAgentId(),
                            assignment.dispatchRequestCreated(), assignment.dispatchRequestId(), assignment.assignmentStatus(), assignment.reason());
                } else if (taskRepository.findById(task.getTaskId())
                        .map(TaskRecord::getNextDispatchAttemptAt)
                        .isPresent()) {
                    deferred++;
                    log.info("task_dispatch_recovery_deferred taskId={} assignmentStatus={} reason={}", task.getTaskId(), assignment.assignmentStatus(), assignment.reason());
                } else {
                    skipped++;
                    log.info("task_dispatch_recovery_skipped taskId={} reason=NO_ASSIGNMENT_NO_RETRY assignmentStatus={} assignmentReason={}",
                            task.getTaskId(), assignment.assignmentStatus(), assignment.reason());
                }
            } catch (RuntimeException ex) {
                failed++;
                OffsetDateTime nextAttemptAt = at.plus(dispatchRecoveryProperties.delayForAttempt(task.getDispatchAttemptCount() + 1));
                String failureReason = "Task dispatch recovery scanner failed: " + rootMessage(ex);
                taskRepository.deferDispatchAttempt(
                        task.getTaskId(),
                        nextAttemptAt,
                        task.getDispatchAttemptCount() + 1,
                        failureReason,
                        at);
                attemptHistoryPort.recordRecoveryScannerFailed(task, failureReason, nextAttemptAt, at);
            } finally {
                taskRepository.clearDispatchRecoveryClaim(
                        task.getTaskId(),
                        firstNonBlank(task.getDispatchRecoveryClaimedBy(), dispatchRecoveryProperties.getWorkerId()),
                        task.getDispatchRecoveryClaimUntil() == null ? claimUntil : task.getDispatchRecoveryClaimUntil(),
                        at);
            }
        }
        TaskDispatchRecoveryScanResult result = new TaskDispatchRecoveryScanResult();
        result.setClaimed(claimed.size());
        result.setRecovered(recovered);
        result.setDeferred(deferred);
        result.setSkipped(skipped);
        result.setFailed(failed);
        result.setMessage("Processed delayed task dispatch recovery");
        return result;
    }

    @Override
    public LifecycleScanResult processLifecycle(TaskLifecyclePolicy policy, OffsetDateTime now) {
        if(policy==null||!policy.timeoutEnabled()) return LifecycleScanResult.empty("Task timeout lifecycle is disabled");
        OffsetDateTime at=effectiveNow(now);
        Duration shortest=List.of(policy.createdTimeout(),policy.assignedTimeout(),policy.dispatchedTimeout(),policy.runningTimeout()).stream().min(Comparator.naturalOrder()).orElse(Duration.ofMinutes(10));
        List<TaskRecord> candidates=taskRepository.findOpenUpdatedBefore(at.minus(shortest),policy.maxBatchSize());
        log.debug("task_lifecycle_scan_candidates cutoff={} shortestTimeout={} candidateCount={} maxBatchSize={}", at.minus(shortest), shortest, candidates.size(), policy.maxBatchSize());
        int timedOut=0,reassigned=0,updated=0,failed=0;
        for(TaskRecord task:candidates){
            if(task==null){continue;}
            try {
                LifecycleCandidateOutcome outcome = tenantExecutionPort.execute(
                        task.getTenantId(),
                        "core-task-lifecycle:" + task.getTaskId(),
                        () -> processLifecycleCandidate(task,policy,at));
                if(outcome.updated()) updated++;
                if(outcome.reassigned()) reassigned++;
                if(outcome.timedOut()) timedOut++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("task_lifecycle_candidate_failed tenantId={} taskId={} status={} reason={}",
                        task.getTenantId(),task.getTaskId(),task.getStatus(),rootMessage(ex),ex);
            }
        }
        LifecycleScanResult result=new LifecycleScanResult();
        result.setScanned(candidates.size());result.setUpdated(updated);result.setReassigned(reassigned);result.setTimedOut(timedOut);
        result.setMessage(failed==0?"Processed task timeout/reassignment lifecycle":"Processed task timeout/reassignment lifecycle; failed="+failed);
        return result;
    }

    private LifecycleCandidateOutcome processLifecycleCandidate(TaskRecord task,TaskLifecyclePolicy policy,OffsetDateTime at){
        if (waitingForScheduledDispatchRetry(task, at)) {
            log.debug("task_lifecycle_scan_skipped tenantId={} taskId={} status={} reason=DISPATCH_RETRY_NOT_DUE nextDispatchAttemptAt={}", task.getTenantId(), task.getTaskId(), task.getStatus(), task.getNextDispatchAttemptAt());
            return LifecycleCandidateOutcome.NONE;
        }
        if(!isTimedOut(task,policy,at)){
            log.debug("task_lifecycle_scan_skipped tenantId={} taskId={} status={} reason=NOT_TIMED_OUT updatedAt={} timeoutAtCandidate={}", task.getTenantId(),task.getTaskId(),task.getStatus(),task.getUpdatedAt(),task.getUpdatedAt()==null?null:task.getUpdatedAt().plus(timeoutFor(task,policy)));
            return LifecycleCandidateOutcome.NONE;
        }
        if (dispatchRecoveryExhausted(task)) {
            log.warn("task_lifecycle_dispatch_recovery_exhausted tenantId={} taskId={} status={} dispatchAttemptCount={} maxAttempts={} reason=DISPATCH_RECOVERY_EXHAUSTED", task.getTenantId(), task.getTaskId(), task.getStatus(), task.getDispatchAttemptCount(), dispatchRecoveryProperties.getMaxAttempts());
            markDispatchRecoveryExhausted(task, at);
            return LifecycleCandidateOutcome.FAILED;
        }
        if(shouldRetryDispatchReady(task,policy)){
            log.info("task_lifecycle_auto_assign_retry_due tenantId={} taskId={} status={} reassignmentCount={} maxReassignments={} dispatchAttemptCount={} dispatchRecoveryMaxAttempts={} updatedAt={} timeout={} reason=DISPATCH_READY_TIMEOUT", task.getTenantId(),task.getTaskId(),task.getStatus(),task.getReassignmentCount(),policy.maxReassignments(),task.getDispatchAttemptCount(),dispatchRecoveryProperties.getMaxAttempts(),task.getUpdatedAt(),timeoutFor(task,policy));
            retryDispatchReadyInternal(task,"Auto-retried dispatch-ready task after lifecycle timeout for status "+task.getStatus(),at);
            return LifecycleCandidateOutcome.REASSIGNED;
        }
        if(shouldReassign(task,policy)){
            log.info("task_lifecycle_auto_reassign_due tenantId={} taskId={} status={} reassignmentCount={} maxReassignments={} updatedAt={} timeout={} reason=TIMEOUT", task.getTenantId(),task.getTaskId(),task.getStatus(),task.getReassignmentCount(),policy.maxReassignments(),task.getUpdatedAt(),timeoutFor(task,policy));
            reassignInternal(task,"Auto-reassigned after lifecycle timeout for status "+task.getStatus(),at);
            return LifecycleCandidateOutcome.REASSIGNED;
        }
        log.warn("task_lifecycle_timeout_due tenantId={} taskId={} status={} reassignmentCount={} maxReassignments={} updatedAt={} timeout={} reason=NO_REASSIGN_AVAILABLE", task.getTenantId(),task.getTaskId(),task.getStatus(),task.getReassignmentCount(),policy.maxReassignments(),task.getUpdatedAt(),timeoutFor(task,policy));
        markTimedOut(task,"Auto-timed-out after lifecycle timeout for status "+task.getStatus(),at);
        return LifecycleCandidateOutcome.TIMED_OUT;
    }

    private enum LifecycleCandidateOutcome {
        NONE(false,false,false),
        REASSIGNED(true,true,false),
        TIMED_OUT(true,false,true),
        FAILED(true,false,false);
        private final boolean updated;
        private final boolean reassigned;
        private final boolean timedOut;
        LifecycleCandidateOutcome(boolean updated,boolean reassigned,boolean timedOut){this.updated=updated;this.reassigned=reassigned;this.timedOut=timedOut;}
        boolean updated(){return updated;}
        boolean reassigned(){return reassigned;}
        boolean timedOut(){return timedOut;}
    }

    private TaskRecord reassignInternal(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;cancelOpenAssignmentAndDispatch(task,reason,now);task.setStatus(TaskStatus.QUEUED);task.setReassignmentCount(task.getReassignmentCount()+1);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);attemptHistoryPort.recordTaskReassigned(saved,reason,now);assignDirect(saved);return saved;}
    private TaskRecord retryDispatchReadyInternal(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;task.setStatus(TaskStatus.QUEUED);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);log.info("task_lifecycle_auto_assign_retry_started taskId={} status={} reason={}", saved.getTaskId(), saved.getStatus(), reason);AssignmentDecisionResult result=assignDirect(saved);log.info("task_lifecycle_auto_assign_retry_completed taskId={} assignmentCreated={} selectedAgentId={} dispatchRequestCreated={} assignmentStatus={} reason={}", saved.getTaskId(), result.assignmentCreated(), result.selectedAgentId(), result.dispatchRequestCreated(), result.assignmentStatus(), firstNonBlank(result.reason(), reason));return saved;}
    private TaskRecord markDispatchRecoveryExhausted(TaskRecord task, OffsetDateTime now) {
        if (isTerminal(task.getStatus())) return task;
        String reason = "Dispatch recovery exhausted after " + task.getDispatchAttemptCount() + " attempts; operator review is required";
        cancelOpenAssignmentAndDispatch(task, reason, now);
        task.setStatus(TaskStatus.FAILED);
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(reason);
        task.setLifecycleReason(reason);
        task.setFailureCode("DISPATCH_RECOVERY_EXHAUSTED");
        task.setFailureAt(now);
        task.setTerminalAt(now);
        task.setUpdatedAt(now);
        TaskRecord saved = taskRepository.save(task);
        attemptHistoryPort.recordRecoveryExhausted(saved, null, reason, now);
        publishTerminal(saved, null, null, null, now);
        return saved;
    }
    private TaskRecord markTimedOut(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;cancelOpenAssignmentAndDispatch(task,reason,now);task.setStatus(TaskStatus.ORPHANED);task.setTimeoutAt(now);task.setTerminalAt(now);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);publishTerminal(saved,null,null,null,now);return saved;}
    private void cancelOpenAssignmentAndDispatch(TaskRecord task,String reason,OffsetDateTime now){assignmentRepository.findOpenByTaskId(task.getTaskId()).ifPresent(a->{a.setStatus(AssignmentStatus.CANCELLED);a.setReason(firstNonBlank(reason,"Lifecycle cancelled assignment"));a.setUpdatedAt(now);assignmentRepository.save(a);taskAssignmentService.releaseCapacityReservation(a.getAssignmentId());executionLifecyclePort.cancelOpenDispatchByAssignment(a.getAssignmentId(),reason,now);});}
    private boolean isTimedOut(TaskRecord task,TaskLifecyclePolicy policy,OffsetDateTime now){if(task==null||task.getUpdatedAt()==null||isTerminal(task.getStatus()))return false;Duration timeout=timeoutFor(task,policy);return !timeout.isZero()&&!timeout.isNegative()&&task.getUpdatedAt().plus(timeout).isBefore(now);}
    private Duration timeoutFor(TaskRecord task,TaskLifecyclePolicy policy){if(task==null||policy==null)return Duration.ZERO;return switch(task.getStatus()){case QUEUED,CREATED,RETRY_WAIT->policy.createdTimeout();case ASSIGNED->policy.assignedTimeout();case DISPATCHED->policy.dispatchedTimeout();case RUNNING->policy.runningTimeout();default->Duration.ZERO;};}
    private boolean waitingForScheduledDispatchRetry(TaskRecord task, OffsetDateTime now) {
        return task != null && task.getStatus() == TaskStatus.RETRY_WAIT && task.getNextDispatchAttemptAt() != null && task.getNextDispatchAttemptAt().isAfter(now);
    }
    private boolean dispatchRecoveryExhausted(TaskRecord task) {
        int maxAttempts = dispatchRecoveryProperties.getMaxAttempts();
        return task != null && maxAttempts > 0 && task.getDispatchAttemptCount() >= maxAttempts
                && (task.getStatus() == TaskStatus.QUEUED || task.getStatus() == TaskStatus.CREATED || task.getStatus() == TaskStatus.RETRY_WAIT);
    }
    private boolean shouldRetryDispatchReady(TaskRecord task,TaskLifecyclePolicy policy){return policy.autoReassignEnabled()&&task!=null&&!dispatchRecoveryExhausted(task)&&(task.getStatus()==TaskStatus.QUEUED||task.getStatus()==TaskStatus.CREATED||task.getStatus()==TaskStatus.RETRY_WAIT);}
    private boolean shouldReassign(TaskRecord task,TaskLifecyclePolicy policy){return policy.autoReassignEnabled()&&task.getReassignmentCount()<policy.maxReassignments()&&(task.getStatus()==TaskStatus.ASSIGNED||task.getStatus()==TaskStatus.DISPATCHED||task.getStatus()==TaskStatus.RUNNING||task.getStatus()==TaskStatus.ORPHANED||task.getStatus()==TaskStatus.RECONCILING);}

    private AssignmentDecisionResult assignDirect(TaskRecord task) {
        if (task != null && (task.getStatus() == TaskStatus.WAITING_HUMAN || task.getStatus() == TaskStatus.BLOCKED)) {
            return AssignmentDecisionResult.none("Task dispatch is held pending Human/Security review");
        }
        return taskAssignmentService == null
                ? AssignmentDecisionResult.none("Standard Dispatch Flow assignment service is unavailable")
                : taskAssignmentService.assignIfPossible(task);
    }
    private boolean isTerminal(TaskStatus s){return s != null && s.isTerminal();}
    private boolean isMissingDispatchRecoverySchema(Throwable exception){Throwable current=exception;while(current!=null){String message=current.getMessage();if(message!=null){String normalized=message.toLowerCase(java.util.Locale.ROOT);boolean missingColumn=normalized.contains("does not exist")&&(normalized.contains("next_dispatch_attempt_at")||normalized.contains("dispatch_attempt_count")||normalized.contains("dispatch_retry_reason")||normalized.contains("dispatch_recovery_claimed_by")||normalized.contains("dispatch_recovery_claim_until"));if(missingColumn)return true;}current=current.getCause();}return false;}
    private String rootMessage(Throwable exception){Throwable current=exception;while(current!=null&&current.getCause()!=null){current=current.getCause();}return current==null?"unknown":(current.getMessage()==null?current.getClass().getName():current.getMessage());}
    private TaskRecord requireTask(String id){return taskRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Task not found: "+id));}
    private OffsetDateTime effectiveNow(OffsetDateTime now){return now==null?OffsetDateTime.now(ZoneOffset.UTC):now;}
    private String firstNonBlank(String... values){if(values==null)return null;for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
    private void publishTerminal(TaskRecord task,String dispatchRequestId,String assignmentId,String callbackType,OffsetDateTime at){eventPublisher.publish(new TaskTerminalEvent("evt-"+UUID.randomUUID(),task.getTaskId(),task.getIncidentId(),task.getSourceEventId(),task.getStatus()==null?null:task.getStatus().name(),task.getTaskType()==null?null:task.getTaskType().name(),task.getPriority()==null?null:task.getPriority().name(),task.getTenantId(),task.getSiteId(),task.getPlantId(),task.getObjectType(),task.getObjectId(),task.getEventType(),task.getErrorCode(),task.getRoutingPolicy(),task.getRequiredCapabilities(),dispatchRequestId,assignmentId,null,null,null,null,callbackType,null,null,null,null,Map.of(),at,
            task.getCorrelationId(),task.getSourceEventId(),task.getTraceId(),null,
            task.getActorPrincipalType(),task.getActorPrincipalId()));}

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> searchTasks(TaskQuery query) {
        return taskRepository.search(query == null ? new TaskQuery() : query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> searchTasks(TaskQuery query, com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan plan) {
        return taskRepository.searchAuthorized(query == null ? new TaskQuery() : query, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> searchTasksTarget(TaskQuery query, com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan plan) {
        return taskRepository.searchAuthorizedTarget(query == null ? new TaskQuery() : query, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> findTasksByIncident(String incidentId, int limit) {
        return taskRepository.findByIncidentId(incidentId, Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRecord> findTasksByIncident(String tenantId, String incidentId, int limit) {
        return taskRepository.findByTenantAndIncidentId(tenantId, incidentId, Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskAssignment> findAssignment(String assignmentId) {
        return assignmentRepository.findById(assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAssignment> recentAssignments(int limit) {
        return assignmentRepository.recent(Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAssignment> findAssignmentsByTask(String taskId, int limit) {
        return assignmentRepository.findByTaskId(taskId, Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoutingDecisionRecord> findRoutingDecision(String decisionId) {
        return routingDecisionRepository.findById(decisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingDecisionRecord> recentRoutingDecisions(int limit) {
        return routingDecisionRepository.recent(Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingDecisionRecord> findRoutingDecisionsByTask(String taskId, int limit) {
        return routingDecisionRepository.findByTaskId(taskId, Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String,Integer> taskStatusCounts(int limit) {
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            TaskQuery q = new TaskQuery();
            q.setStatus(status);
            q.setLimit(Math.max(1, limit));
            counts.put(status.name(), taskRepository.search(q).size());
        }
        return counts;
    }
    @Override public String taskStoreMode(){return taskRepository.mode();}
    @Override public String assignmentStoreMode(){return assignmentRepository.mode();}
    @Override public String routingStoreMode(){return routingDecisionRepository.mode();}
}
