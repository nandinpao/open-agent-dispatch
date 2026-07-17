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

    @Override
    @Transactional
    public AssignmentDecisionResult recoverTaskDispatchNow(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new IllegalStateException("Terminal task cannot be recovered immediately: " + taskId + " status=" + task.getStatus());
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
    @Override public Optional<TaskRecord> findTask(String taskId) { return taskRepository.findById(taskId); }
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
    @Transactional
    public LifecycleScanResult processLifecycle(TaskLifecyclePolicy policy, OffsetDateTime now) {
        if(policy==null||!policy.timeoutEnabled()) return LifecycleScanResult.empty("Task timeout lifecycle is disabled");
        OffsetDateTime at=effectiveNow(now);
        Duration shortest=List.of(policy.createdTimeout(),policy.assignedTimeout(),policy.dispatchedTimeout(),policy.runningTimeout()).stream().min(Comparator.naturalOrder()).orElse(Duration.ofMinutes(10));
        List<TaskRecord> candidates=taskRepository.findOpenUpdatedBefore(at.minus(shortest),policy.maxBatchSize());
        log.debug("task_lifecycle_scan_candidates cutoff={} shortestTimeout={} candidateCount={} maxBatchSize={}", at.minus(shortest), shortest, candidates.size(), policy.maxBatchSize());
        int timedOut=0,reassigned=0,updated=0;
        for(TaskRecord task:candidates){
            if(!isTimedOut(task,policy,at)){
                log.debug("task_lifecycle_scan_skipped taskId={} status={} reason=NOT_TIMED_OUT updatedAt={} timeoutAtCandidate={}", task.getTaskId(), task.getStatus(), task.getUpdatedAt(), task.getUpdatedAt()==null?null:task.getUpdatedAt().plus(timeoutFor(task,policy)));
                continue;
            }
            if(shouldRetryDispatchReady(task,policy)){
                log.info("task_lifecycle_auto_assign_retry_due taskId={} status={} reassignmentCount={} maxReassignments={} updatedAt={} timeout={} reason=DISPATCH_READY_TIMEOUT", task.getTaskId(), task.getStatus(), task.getReassignmentCount(), policy.maxReassignments(), task.getUpdatedAt(), timeoutFor(task,policy));
                retryDispatchReadyInternal(task,"Auto-retried dispatch-ready task after lifecycle timeout for status "+task.getStatus(),at);reassigned++;
            }else if(shouldReassign(task,policy)){
                log.info("task_lifecycle_auto_reassign_due taskId={} status={} reassignmentCount={} maxReassignments={} updatedAt={} timeout={} reason=TIMEOUT", task.getTaskId(), task.getStatus(), task.getReassignmentCount(), policy.maxReassignments(), task.getUpdatedAt(), timeoutFor(task,policy));
                reassignInternal(task,"Auto-reassigned after lifecycle timeout for status "+task.getStatus(),at);reassigned++;
            }else{
                log.warn("task_lifecycle_timeout_due taskId={} status={} reassignmentCount={} maxReassignments={} updatedAt={} timeout={} reason=NO_REASSIGN_AVAILABLE", task.getTaskId(), task.getStatus(), task.getReassignmentCount(), policy.maxReassignments(), task.getUpdatedAt(), timeoutFor(task,policy));
                markTimedOut(task,"Auto-timed-out after lifecycle timeout for status "+task.getStatus(),at);timedOut++;}
            updated++;}
        LifecycleScanResult result=new LifecycleScanResult();result.setScanned(candidates.size());result.setUpdated(updated);result.setReassigned(reassigned);result.setTimedOut(timedOut);result.setMessage("Processed task timeout/reassignment lifecycle");return result;
    }

    private TaskRecord reassignInternal(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;cancelOpenAssignmentAndDispatch(task,reason,now);task.setStatus(TaskStatus.QUEUED);task.setReassignmentCount(task.getReassignmentCount()+1);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);attemptHistoryPort.recordTaskReassigned(saved,reason,now);assignDirect(saved);return saved;}
    private TaskRecord retryDispatchReadyInternal(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;task.setStatus(TaskStatus.QUEUED);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);log.info("task_lifecycle_auto_assign_retry_started taskId={} status={} reason={}", saved.getTaskId(), saved.getStatus(), reason);AssignmentDecisionResult result=assignDirect(saved);log.info("task_lifecycle_auto_assign_retry_completed taskId={} assignmentCreated={} selectedAgentId={} dispatchRequestCreated={} assignmentStatus={} reason={}", saved.getTaskId(), result.assignmentCreated(), result.selectedAgentId(), result.dispatchRequestCreated(), result.assignmentStatus(), firstNonBlank(result.reason(), reason));return saved;}
    private TaskRecord markTimedOut(TaskRecord task,String reason,OffsetDateTime now){if(isTerminal(task.getStatus()))return task;cancelOpenAssignmentAndDispatch(task,reason,now);task.setStatus(TaskStatus.ORPHANED);task.setTimeoutAt(now);task.setTerminalAt(now);task.setUpdatedAt(now);task.setLifecycleReason(reason);TaskRecord saved=taskRepository.save(task);publishTerminal(saved,null,null,null,now);return saved;}
    private void cancelOpenAssignmentAndDispatch(TaskRecord task,String reason,OffsetDateTime now){assignmentRepository.findOpenByTaskId(task.getTaskId()).ifPresent(a->{a.setStatus(AssignmentStatus.CANCELLED);a.setReason(firstNonBlank(reason,"Lifecycle cancelled assignment"));a.setUpdatedAt(now);assignmentRepository.save(a);taskAssignmentService.releaseCapacityReservation(a.getAssignmentId());executionLifecyclePort.cancelOpenDispatchByAssignment(a.getAssignmentId(),reason,now);});}
    private boolean isTimedOut(TaskRecord task,TaskLifecyclePolicy policy,OffsetDateTime now){if(task==null||task.getUpdatedAt()==null||isTerminal(task.getStatus()))return false;Duration timeout=timeoutFor(task,policy);return !timeout.isZero()&&!timeout.isNegative()&&task.getUpdatedAt().plus(timeout).isBefore(now);}
    private Duration timeoutFor(TaskRecord task,TaskLifecyclePolicy policy){if(task==null||policy==null)return Duration.ZERO;return switch(task.getStatus()){case QUEUED,CREATED,RETRY_WAIT->policy.createdTimeout();case ASSIGNED->policy.assignedTimeout();case DISPATCHED->policy.dispatchedTimeout();case RUNNING->policy.runningTimeout();default->Duration.ZERO;};}
    private boolean shouldRetryDispatchReady(TaskRecord task,TaskLifecyclePolicy policy){return policy.autoReassignEnabled()&&task!=null&&(task.getStatus()==TaskStatus.QUEUED||task.getStatus()==TaskStatus.CREATED||task.getStatus()==TaskStatus.RETRY_WAIT);}
    private boolean shouldReassign(TaskRecord task,TaskLifecyclePolicy policy){return policy.autoReassignEnabled()&&task.getReassignmentCount()<policy.maxReassignments()&&(task.getStatus()==TaskStatus.ASSIGNED||task.getStatus()==TaskStatus.DISPATCHED||task.getStatus()==TaskStatus.RUNNING||task.getStatus()==TaskStatus.ORPHANED||task.getStatus()==TaskStatus.RECONCILING);}

    private AssignmentDecisionResult assignDirect(TaskRecord task) {
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
    private void publishTerminal(TaskRecord task,String dispatchRequestId,String assignmentId,String callbackType,OffsetDateTime at){eventPublisher.publish(new TaskTerminalEvent("evt-"+UUID.randomUUID(),task.getTaskId(),task.getIncidentId(),task.getSourceEventId(),task.getStatus()==null?null:task.getStatus().name(),task.getTaskType()==null?null:task.getTaskType().name(),task.getPriority()==null?null:task.getPriority().name(),task.getTenantId(),task.getSiteId(),task.getPlantId(),task.getObjectType(),task.getObjectId(),task.getEventType(),task.getErrorCode(),task.getRoutingPolicy(),task.getRequiredCapabilities(),dispatchRequestId,assignmentId,null,null,null,null,callbackType,null,null,null,null,Map.of(),at));}

    @Override public List<TaskRecord> searchTasks(TaskQuery query) { return taskRepository.search(query == null ? new TaskQuery() : query); }
    @Override public List<TaskRecord> findTasksByIncident(String incidentId, int limit) { return taskRepository.findByIncidentId(incidentId, Math.max(1, limit)); }
    @Override public Optional<TaskAssignment> findAssignment(String assignmentId) { return assignmentRepository.findById(assignmentId); }
    @Override public List<TaskAssignment> recentAssignments(int limit) { return assignmentRepository.recent(Math.max(1, limit)); }
    @Override public List<TaskAssignment> findAssignmentsByTask(String taskId, int limit) { return assignmentRepository.findByTaskId(taskId, Math.max(1, limit)); }
    @Override public Optional<RoutingDecisionRecord> findRoutingDecision(String decisionId) { return routingDecisionRepository.findById(decisionId); }
    @Override public List<RoutingDecisionRecord> recentRoutingDecisions(int limit) { return routingDecisionRepository.recent(Math.max(1, limit)); }
    @Override public List<RoutingDecisionRecord> findRoutingDecisionsByTask(String taskId, int limit) { return routingDecisionRepository.findByTaskId(taskId, Math.max(1, limit)); }
    @Override public Map<String,Integer> taskStatusCounts(int limit){Map<String,Integer> counts=new LinkedHashMap<>();for(TaskStatus status:TaskStatus.values()){TaskQuery q=new TaskQuery();q.setStatus(status);q.setLimit(Math.max(1,limit));counts.put(status.name(),taskRepository.search(q).size());}return counts;}
    @Override public String taskStoreMode(){return taskRepository.mode();}
    @Override public String assignmentStoreMode(){return assignmentRepository.mode();}
    @Override public String routingStoreMode(){return routingDecisionRepository.mode();}
}
