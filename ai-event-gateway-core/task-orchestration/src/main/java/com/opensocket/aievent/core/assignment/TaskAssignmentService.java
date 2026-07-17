package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.CapacityReservationResult;
import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.DispatchUserFacingErrorCode;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryProperties;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;

@Service
public class TaskAssignmentService {
    private final RoutingDecisionService routingDecisionService;
    private final TaskAssignmentRepository assignmentRepository;
    private final AgentDirectoryFacade agentDirectory;
    private final TaskRepository taskRepository;
    private final RoutingProperties properties;
    private final TaskDispatchRecoveryProperties dispatchRecoveryProperties;
    private final TaskDispatchPort dispatchPort;

    @Autowired(required = false)
    private TaskDispatchAttemptHistoryPort attemptHistoryPort = TaskDispatchAttemptHistoryPort.noop();

    @Autowired
    public TaskAssignmentService(RoutingDecisionService routingDecisionService,
                                 TaskAssignmentRepository assignmentRepository,
                                 AgentDirectoryFacade agentDirectory,
                                 TaskRepository taskRepository,
                                 RoutingProperties properties,
                                 TaskDispatchRecoveryProperties dispatchRecoveryProperties,
                                 TaskDispatchPort dispatchPort) {
        this.routingDecisionService = routingDecisionService;
        this.assignmentRepository = assignmentRepository;
        this.agentDirectory = agentDirectory;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.dispatchRecoveryProperties = dispatchRecoveryProperties == null ? new TaskDispatchRecoveryProperties() : dispatchRecoveryProperties;
        this.dispatchPort = dispatchPort;
    }

    /** Compatibility constructor for focused unit tests. */
    public TaskAssignmentService(RoutingDecisionService routingDecisionService,
                                 TaskAssignmentRepository assignmentRepository,
                                 AgentDirectoryFacade agentDirectory,
                                 TaskRepository taskRepository,
                                 RoutingProperties properties,
                                 TaskDispatchPort dispatchPort) {
        this(routingDecisionService, assignmentRepository, agentDirectory, taskRepository, properties,
                new TaskDispatchRecoveryProperties(), dispatchPort);
    }

    @Transactional
    public AssignmentDecisionResult assignIfPossible(TaskRecord task) {
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> {
                    clearPendingDispatchDelay(task, "Task already has open assignment " + existing.getAssignmentId());
                    attemptHistoryPort.recordAssignmentReused(task, existing, "Task already has open assignment " + existing.getAssignmentId(), OffsetDateTime.now(ZoneOffset.UTC));
                    return AssignmentDecisionResult.withDispatch(
                            false,
                            existing.getAssignmentId(),
                            existing.getAgentId(),
                            existing.getOwnerGatewayNodeId(),
                            existing.getAgentSessionId(),
                            existing.getSiteId(),
                            existing.getRoutingDecisionId(),
                            existing.getStatus().name(),
                            "Task already has open assignment " + existing.getAssignmentId(),
                            dispatchPort.createIfEligible(existing, task));
                })
                .orElseGet(() -> createFromRoutingDecision(task));
    }

    @Transactional
    public boolean releaseCapacityReservation(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return false;
        }
        TaskAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null || !assignment.isCapacityReserved()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!assignmentRepository.releaseCapacityReservation(assignmentId, now)) {
            return false;
        }
        agentDirectory.releaseCapacity(assignment.getAgentId());
        assignment.setCapacityReserved(false);
        assignment.setCapacityReleasedAt(now);
        return true;
    }

    private AssignmentDecisionResult createFromRoutingDecision(TaskRecord task) {
        Set<String> excluded = new HashSet<>();
        int maxAttempts = Math.max(1, properties.getMaxCandidates());
        RoutingDecisionRecord lastDecision = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            RoutingDecisionRecord decision = routingDecisionService.decide(task, excluded);
            lastDecision = decision;
            if (decision.getStatus() != RoutingDecisionStatus.SELECTED) {
                return noAssignment(task, decision);
            }

            CapacityReservationResult reservation = agentDirectory.reserveCapacity(decision.getSelectedAgentId());
            if (!reservation.reserved()) {
                excluded.add(decision.getSelectedAgentId());
                continue;
            }
            return persistReservedAssignment(task, decision);
        }
        String reason = lastDecision == null
                ? "No routing decision was produced"
                : "All selected candidates lost capacity before reservation; lastDecision=" + lastDecision.getDecisionId();
        return deferAssignmentRetry(task,
                lastDecision == null ? null : lastDecision.getDecisionId(),
                RoutingDecisionStatus.NO_CANDIDATE.name(),
                reason);
    }

    private AssignmentDecisionResult persistReservedAssignment(TaskRecord task, RoutingDecisionRecord decision) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(decision.getSelectedAgentId());
        AgentSnapshot agent = agentDirectory.findById(decision.getSelectedAgentId()).orElse(null);
        assignment.setAgentType(agent == null ? null : agent.getAgentType());
        assignment.setOwnerGatewayNodeId(decision.getSelectedGatewayNodeId());
        assignment.setAgentSessionId(decision.getSelectedAgentSessionId());
        assignment.setSiteId(decision.getSelectedSiteId());
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy(decision.getRoutingPolicy().name());
        assignment.setRoutingDecisionId(decision.getDecisionId());
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(decision.getSelectedScore());
        assignment.setReason(decision.getDecisionReason() + "; agent capacity reserved atomically before assignment persistence");
        assignment.setCapacityReserved(true);
        assignment.setCapacityReservedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);

        TaskAssignment saved = null;
        try {
            saved = assignmentRepository.save(assignment);
            if (properties.isUpdateTaskStatusOnAssignment()) {
                task.setStatus(TaskStatus.ASSIGNED);
                task.setAssignedPoolId(firstNonBlank(task.getAssignedPoolId(), task.getTargetPoolId()));
                task.setNextDispatchAttemptAt(null);
                task.setDispatchRetryReason(null);
                task.setDispatchRecoveryClaimedBy(null);
                task.setDispatchRecoveryClaimUntil(null);
                task.setUpdatedAt(now);
                task.setLifecycleReason("Assigned to agent " + saved.getAgentId());
                taskRepository.save(task);
            }
            attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
            DispatchDecisionResult dispatch = dispatchPort.createIfEligible(saved, task);
            return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), saved.getAgentId(), saved.getOwnerGatewayNodeId(),
                    saved.getAgentSessionId(), saved.getSiteId(), saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(), dispatch);
        } catch (RuntimeException ex) {
            if (saved != null) {
                assignmentRepository.releaseCapacityReservation(saved.getAssignmentId(), OffsetDateTime.now(ZoneOffset.UTC));
            }
            agentDirectory.releaseCapacity(decision.getSelectedAgentId());
            throw ex;
        }
    }



    @Transactional
    public AssignmentDecisionResult assignToSpecificAgent(TaskRecord task, String agentId, String reason) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return AssignmentDecisionResult.none("Task is required for target-agent assignment");
        }
        if (agentId == null || agentId.isBlank()) {
            return AssignmentDecisionResult.none("agentId is required for target-agent assignment");
        }
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> AssignmentDecisionResult.withDispatch(
                        false,
                        existing.getAssignmentId(),
                        existing.getAgentId(),
                        existing.getOwnerGatewayNodeId(),
                        existing.getAgentSessionId(),
                        existing.getSiteId(),
                        existing.getRoutingDecisionId(),
                        existing.getStatus().name(),
                        "Task already has open assignment " + existing.getAssignmentId(),
                        dispatchPort.createIfEligible(existing, task)))
                .orElseGet(() -> persistReservedTargetAgentAssignment(task, agentId, reason));
    }

    private AssignmentDecisionResult persistReservedTargetAgentAssignment(TaskRecord task, String agentId, String reason) {
        AgentSnapshot agent = agentDirectory.findById(agentId).orElse(null);
        if (agent == null) {
            return AssignmentDecisionResult.none("Target agent not found: " + agentId);
        }
        if (!agent.isAssignable()) {
            return AssignmentDecisionResult.none("Target agent is not assignable: " + agentId);
        }
        CapacityReservationResult reservation = agentDirectory.reserveCapacity(agentId);
        if (!reservation.reserved()) {
            return AssignmentDecisionResult.none("Target agent capacity reservation failed: " + reservation.reason());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(agentId);
        assignment.setAgentType(agent.getAgentType());
        assignment.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        assignment.setAgentSessionId(agent.getAgentSessionId());
        assignment.setSiteId(agent.getSiteId());
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy("CERTIFICATION_TARGET_AGENT");
        assignment.setRoutingDecisionId("cert-routing-" + UUID.randomUUID());
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(100);
        assignment.setReason(firstNonBlank(reason, "Certification task pinned to target agent " + agentId));
        assignment.setCapacityReserved(true);
        assignment.setCapacityReservedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        try {
            TaskAssignment saved = assignmentRepository.save(assignment);
            task.setStatus(TaskStatus.ASSIGNED);
            task.setUpdatedAt(now);
            task.setLifecycleReason("Certification task assigned to target agent " + saved.getAgentId());
            taskRepository.save(task);
            attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
            DispatchDecisionResult dispatch = dispatchPort.createIfEligible(saved, task);
            return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), saved.getAgentId(), saved.getOwnerGatewayNodeId(),
                    saved.getAgentSessionId(), saved.getSiteId(), saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(), dispatch);
        } catch (RuntimeException ex) {
            agentDirectory.releaseCapacity(agentId);
            throw ex;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private void clearPendingDispatchDelay(TaskRecord task, String reason) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        if (task.getNextDispatchAttemptAt() == null
                && task.getDispatchRetryReason() == null
                && task.getDispatchRecoveryClaimedBy() == null
                && task.getDispatchRecoveryClaimUntil() == null) {
            return;
        }
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(null);
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setLifecycleReason(reason);
        taskRepository.save(task);
    }

    private AssignmentDecisionResult noAssignment(TaskRecord task, RoutingDecisionRecord decision) {
        persistRoutingEvidence(task, decision);
        String configurationBlocker = configurationBlockerCode(task, decision.getDecisionReason());
        if (configurationBlocker != null) {
            return suspendUntilConfigurationChange(task, decision.getDecisionId(), decision.getStatus().name(), configurationBlocker, decision.getDecisionReason());
        }
        if (decision.getStatus() == RoutingDecisionStatus.NO_CANDIDATE) {
            return deferAssignmentRetry(task, decision.getDecisionId(), decision.getStatus().name(), decision.getDecisionReason());
        }
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null,
                decision.getDecisionId(), decision.getStatus().name(), decision.getDecisionReason(),
                DispatchDecisionResult.none("No assignment was created"));
    }

    private void persistRoutingEvidence(TaskRecord task, RoutingDecisionRecord decision) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        if (task.getTargetPoolId() == null && task.getAssignedPoolId() == null
                && task.getMatchedFlowId() == null && task.getMatchedRuleId() == null) {
            return;
        }
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setLifecycleReason(firstNonBlank(task.getLifecycleReason(), decision == null ? null : decision.getDecisionReason(), "Routing evidence recorded"));
        taskRepository.save(task);
    }

    private AssignmentDecisionResult suspendUntilConfigurationChange(TaskRecord task, String routingDecisionId, String status, String blockerCode, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String message = "WAITING_CONFIGURATION:" + blockerCode + ":" + stripTechnicalDetails(reason);
        taskRepository.suspendDispatchUntilConfigurationChange(task.getTaskId(), blockerCode, stripTechnicalDetails(reason), now);
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(message);
        task.setLifecycleReason("Waiting for dispatch configuration change: " + blockerCode);
        attemptHistoryPort.recordDelayedDispatch(task, routingDecisionId, message, null, now);
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, message,
                DispatchDecisionResult.none("No assignment was created; task is waiting for a dispatch configuration change"));
    }

    private String configurationBlockerCode(TaskRecord task, String reason) {
        String normalized = ((reason == null ? "" : reason) + " " + (task == null ? "" : String.valueOf(task.getRoutingPath()))).toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("SOURCE_FLOW_NOT_FOUND")) return "SOURCE_FLOW_NOT_FOUND";
        if (normalized.contains("SOURCE_FLOW_HAS_NO_DEFAULT_POOL")) return "SOURCE_FLOW_HAS_NO_DEFAULT_POOL";
        if (normalized.contains("RULE_TARGET_POOL_NOT_FOUND")) return "RULE_TARGET_POOL_NOT_FOUND";
        if (normalized.contains("POOL_HAS_NO_ACTIVE_MEMBER")) return "POOL_HAS_NO_ACTIVE_MEMBER";
        if (normalized.contains("POOL_AGENT_RUNTIME_NOT_FOUND")) return "POOL_AGENT_RUNTIME_NOT_FOUND";
        if (normalized.contains("POOL_AGENT_OFFLINE")) return "POOL_AGENT_OFFLINE";
        if (normalized.contains("POOL_AGENT_CAPACITY_FULL")) return "POOL_AGENT_CAPACITY_FULL";
        if (normalized.contains("POOL_AGENT_BACKOFF")) return "POOL_AGENT_BACKOFF";
        if (normalized.contains("NO_ELIGIBLE_AGENT_IN_POOL")) return "NO_ELIGIBLE_AGENT_IN_POOL";
        if (normalized.contains("NO_ACTIVE_FLOW_RULE") || normalized.contains("FLOW_RULE_REQUIRED_BLOCKED")) return "SOURCE_FLOW_NOT_FOUND";
        if (normalized.contains("NO_FLOW_SELECTED_AGENT") || normalized.contains("FLOW_SELECTED_AGENT_REQUIRED")) return "NO_ELIGIBLE_AGENT_IN_POOL";
        return null;
    }


    private String userFacingDelayedDispatchReason(OffsetDateTime nextAttemptAt, String status, String reason) {
        String readableReason = stripTechnicalDetails(reason);
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_DELAYED_NO_ELIGIBLE_AGENT,
                "MEDIUM",
                "目標 Agent Pool 目前沒有可用成員可派工，系統已安排稍後自動重試。原因：" + readableReason + " 下次重試：" + nextAttemptAt,
                "請先檢查 Source Flow default Pool / Rule target Pool 是否正確，Pool 內是否有已核准、已連線且有容量的 Agent；修正後系統會依重試時間再次派工。",
                "runbooks/dispatch/pool-first-delayed-no-eligible-agent",
                details("routingStatus", status),
                details("nextDispatchAttemptAt", nextAttemptAt, "routingStatus", status, "routingReason", reason)
        ).toLegacyDecisionReason();
    }

    private String stripTechnicalDetails(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Core routing 回報沒有可派工 Agent。";
        }
        int marker = reason.indexOf("Technical details:");
        return marker >= 0 ? reason.substring(0, marker).trim() : reason.trim();
    }

    private String userFacingRecoveryExhaustedReason(int attempts, String routingDecisionId, String status, String reason) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_RECOVERY_EXHAUSTED,
                "HIGH",
                "Dispatch recovery 已達最大重試次數，任務已轉為失敗狀態。原因：" + stripTechnicalDetails(reason),
                "請由 Operator 檢查 Dispatch Flow、Agent 核准狀態、Runtime 與容量後，決定重新開啟、改派或取消此任務。",
                "runbooks/dispatch/recovery-exhausted",
                details("routingDecisionId", routingDecisionId, "routingStatus", status),
                details("attempts", attempts, "routingDecisionId", routingDecisionId, "routingStatus", status, "routingReason", reason)
        ).toLegacyDecisionReason();
    }

    private Map<String, Object> details(Object... keyValues) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) {
            return values;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                values.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        return values;
    }

    private void copyR3EnvelopeTrace(TaskRecord task, TaskAssignment assignment) {
        if (task == null || assignment == null) {
            return;
        }
        assignment.setEventStage(task.getEventStage());
        assignment.setOriginSourceSystem(task.getOriginSourceSystem());
        assignment.setTargetSystem(task.getTargetSystem());
        assignment.setRequestedSkill(task.getRequestedSkill());
        assignment.setCorrelationId(task.getCorrelationId());
        assignment.setParentTaskId(task.getParentTaskId());
        assignment.setHandoffMode(task.getHandoffMode());
        assignment.setMatchedFlowId(task.getMatchedFlowId());
        assignment.setMatchedRuleId(task.getMatchedRuleId());
        assignment.setAssignedPoolId(firstNonBlank(task.getAssignedPoolId(), task.getTargetPoolId()));
        assignment.setTargetPoolId(task.getTargetPoolId());
        assignment.setRoutingPath(task.getRoutingPath());
    }

    private AssignmentDecisionResult deferAssignmentRetry(TaskRecord task, String routingDecisionId, String status, String reason) {
        if (task == null || task.getTaskId() == null || !dispatchRecoveryProperties.isEnabled()) {
            return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, reason,
                    DispatchDecisionResult.none("No assignment was created"));
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int nextAttempt = task.getDispatchAttemptCount() + 1;
        if (dispatchRecoveryProperties.getMaxAttempts() > 0
                && nextAttempt > dispatchRecoveryProperties.getMaxAttempts()) {
            task.setStatus(TaskStatus.FAILED);
            task.setTerminalAt(now);
            task.setUpdatedAt(now);
            task.setDispatchRetryReason(userFacingRecoveryExhaustedReason(task.getDispatchAttemptCount(), routingDecisionId, status, reason));
            task.setLifecycleReason(task.getDispatchRetryReason());
            taskRepository.save(task);
            attemptHistoryPort.recordRecoveryExhausted(task, routingDecisionId, task.getDispatchRetryReason(), now);
            return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status,
                    task.getDispatchRetryReason(), DispatchDecisionResult.none("No assignment was created; task dispatch recovery exhausted"));
        }
        OffsetDateTime nextAttemptAt = now.plus(dispatchRecoveryProperties.delayForAttempt(nextAttempt));
        String recoveryReason = userFacingDelayedDispatchReason(nextAttemptAt, status, reason);
        taskRepository.deferDispatchAttempt(task.getTaskId(), nextAttemptAt, nextAttempt, recoveryReason, now);
        task.setNextDispatchAttemptAt(nextAttemptAt);
        task.setDispatchAttemptCount(nextAttempt);
        task.setDispatchRetryReason(recoveryReason);
        attemptHistoryPort.recordDelayedDispatch(task, routingDecisionId, recoveryReason, nextAttemptAt, now);
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, recoveryReason,
                DispatchDecisionResult.none("No assignment was created; task dispatch recovery scheduled at " + nextAttemptAt));
    }
}

