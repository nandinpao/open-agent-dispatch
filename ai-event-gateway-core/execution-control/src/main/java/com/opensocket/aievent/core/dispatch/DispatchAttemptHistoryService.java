package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptHistoryPort;
import com.opensocket.aievent.core.task.TaskRecord;

import tools.jackson.databind.ObjectMapper;

@Service
public class DispatchAttemptHistoryService implements TaskDispatchAttemptHistoryPort {
    private static final Logger log = LoggerFactory.getLogger(DispatchAttemptHistoryService.class);
    private final AtomicBoolean missingSchemaWarningLogged = new AtomicBoolean(false);
    public static final String EVENT_DISPATCH_REQUEST_CREATED = "DISPATCH_REQUEST_CREATED";
    public static final String EVENT_DISPATCH_ATTEMPT_STARTED = "DISPATCH_ATTEMPT_STARTED";
    public static final String EVENT_GATEWAY_DELIVERED = "GATEWAY_DELIVERED";
    public static final String EVENT_GATEWAY_DELIVERED_UNCONFIRMED = "GATEWAY_DELIVERED_UNCONFIRMED";
    public static final String EVENT_GATEWAY_RETRY_WAITING = "GATEWAY_RETRY_WAITING";
    public static final String EVENT_RUNTIME_DELIVERY_FAILED = "RUNTIME_DELIVERY_FAILED";
    public static final String EVENT_RUNTIME_BACKOFF_APPLIED = "RUNTIME_BACKOFF_APPLIED";
    public static final String EVENT_TASK_REQUEUED = "TASK_REQUEUED";
    public static final String EVENT_DEAD_LETTERED = "DEAD_LETTERED";
    public static final String EVENT_ASSIGNMENT_CREATED = "ASSIGNMENT_CREATED";
    public static final String EVENT_ASSIGNMENT_REUSED = "ASSIGNMENT_REUSED";
    public static final String EVENT_DELAYED_REQUEUE_SCHEDULED = "DELAYED_REQUEUE_SCHEDULED";
    public static final String EVENT_DELAYED_REQUEUE_CLAIMED = "DELAYED_REQUEUE_CLAIMED";
    public static final String EVENT_DELAYED_REQUEUE_FAILED = "DELAYED_REQUEUE_FAILED";
    public static final String EVENT_RECOVERY_EXHAUSTED = "RECOVERY_EXHAUSTED";
    public static final String EVENT_TASK_REASSIGNED = "TASK_REASSIGNED";
    public static final String EVENT_OPERATOR_RUNTIME_BACKOFF_CLEARED = "OPERATOR_RUNTIME_BACKOFF_CLEARED";
    public static final String EVENT_OPERATOR_RECOVERY_TRIGGERED = "OPERATOR_RECOVERY_TRIGGERED";
    public static final String EVENT_OPERATOR_DEAD_LETTER_MOVED = "OPERATOR_DEAD_LETTER_MOVED";
    public static final String EVENT_OPERATOR_DEAD_LETTER_RESTORED = "OPERATOR_DEAD_LETTER_RESTORED";
    public static final String EVENT_RECOVERY_APPROVAL_REQUESTED = "RECOVERY_APPROVAL_REQUESTED";
    public static final String EVENT_RECOVERY_APPROVAL_APPROVED = "RECOVERY_APPROVAL_APPROVED";
    public static final String EVENT_RECOVERY_APPROVAL_REJECTED = "RECOVERY_APPROVAL_REJECTED";
    public static final String EVENT_RECOVERY_APPROVAL_CANCELLED = "RECOVERY_APPROVAL_CANCELLED";
    public static final String EVENT_RECOVERY_APPROVAL_EXECUTED = "RECOVERY_APPROVAL_EXECUTED";
    public static final String EVENT_RECOVERY_APPROVAL_FAILED = "RECOVERY_APPROVAL_FAILED";

    private final DispatchAttemptHistoryRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private DispatchRecoveryMetricsPort recoveryMetricsPort = DispatchRecoveryMetricsPort.noop();

    public DispatchAttemptHistoryService(DispatchAttemptHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public DispatchAttemptHistoryRecord recordDispatchRequestCreated(DispatchRequest request, String reason, OffsetDateTime occurredAt) {
        return append(fromRequest(request, EVENT_DISPATCH_REQUEST_CREATED, request == null ? null : request.getStatus(), reason, occurredAt));
    }

    public DispatchAttemptHistoryRecord recordAttemptStarted(DispatchRequest request, OffsetDateTime occurredAt) {
        return append(fromRequest(request, EVENT_DISPATCH_ATTEMPT_STARTED, DispatchRequestStatus.DISPATCHING, "Dispatch request claimed for gateway delivery", occurredAt));
    }

    public DispatchAttemptHistoryRecord recordGatewayDelivered(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_GATEWAY_DELIVERED, DispatchRequestStatus.DISPATCHED,
                "Netty dispatch accepted: " + safe(gatewayResult == null ? null : gatewayResult.message()), occurredAt);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        record.setPayloadJson(toJson(gatewayPayload(gatewayResult)));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordGatewayDeliveredUnconfirmed(DispatchRequest request, GatewayDispatchResult gatewayResult, String writeOutcome, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_GATEWAY_DELIVERED_UNCONFIRMED, DispatchRequestStatus.DISPATCHING,
                "Netty dispatch was accepted, but Core lost the claim before persisting DISPATCHED. Callback reconciliation must rely on dispatchToken and attemptNo.", occurredAt);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        Map<String, Object> payload = gatewayPayload(gatewayResult);
        payload.put("writeOutcome", writeOutcome);
        payload.put("dispatchAuthPresent", request != null && !blank(request.getDispatchToken()));
        payload.put("dispatchAuthFingerprint", fingerprint(request == null ? null : request.getDispatchToken()));
        payload.put("attemptNo", request == null ? null : request.getAttemptCount());
        record.setPayloadJson(toJson(payload));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordRetryWaiting(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime nextRetryAt, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_GATEWAY_RETRY_WAITING, DispatchRequestStatus.RETRY_WAITING,
                "Netty dispatch failed; retry scheduled", occurredAt);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        record.setErrorMessage(gatewayResult == null ? null : gatewayResult.message());
        record.setNextAttemptAt(nextRetryAt);
        record.setPayloadJson(toJson(gatewayPayload(gatewayResult)));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordRuntimeDeliveryFailed(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_RUNTIME_DELIVERY_FAILED, DispatchRequestStatus.FAILED,
                "Runtime delivery failure triggered task requeue", occurredAt);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        record.setErrorMessage(gatewayResult == null ? null : gatewayResult.message());
        record.setPayloadJson(toJson(gatewayPayload(gatewayResult)));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordRuntimeBackoffApplied(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime runtimeBackoffUntil, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_RUNTIME_BACKOFF_APPLIED, DispatchRequestStatus.FAILED,
                "Failed agent placed in runtime backoff until " + runtimeBackoffUntil, occurredAt);
        record.setRuntimeBackoffUntil(runtimeBackoffUntil);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        record.setErrorMessage(gatewayResult == null ? null : gatewayResult.message());
        record.setPayloadJson(toJson(gatewayPayload(gatewayResult)));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordTaskRequeued(DispatchRequest request, TaskRecord task, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_TASK_REQUEUED, request == null ? null : request.getStatus(), reason, occurredAt);
        if (task != null) {
            record.setTaskDispatchAttemptNo(task.getDispatchAttemptCount());
            record.setNextAttemptAt(task.getNextDispatchAttemptAt());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskStatus", task.getStatus() == null ? null : task.getStatus().name());
            payload.put("reassignmentCount", task.getReassignmentCount());
            payload.put("dispatchAttemptCount", task.getDispatchAttemptCount());
            payload.put("dispatchRetryReason", safe(task.getDispatchRetryReason()));
            record.setPayloadJson(toJson(payload));
        }
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordDeadLettered(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_DEAD_LETTERED, DispatchRequestStatus.DEAD_LETTER,
                request == null ? null : request.getReason(), occurredAt);
        record.setErrorCode(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        record.setErrorMessage(gatewayResult == null ? null : gatewayResult.message());
        record.setPayloadJson(toJson(gatewayPayload(gatewayResult)));
        return append(record);
    }

    @Override
    public void recordAssignmentCreated(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt) {
        append(fromTaskAndAssignment(task, assignment, EVENT_ASSIGNMENT_CREATED, assignment == null ? null : assignment.getStatus().name(), reason, occurredAt));
    }

    @Override
    public void recordAssignmentReused(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt) {
        append(fromTaskAndAssignment(task, assignment, EVENT_ASSIGNMENT_REUSED, assignment == null ? null : assignment.getStatus().name(), reason, occurredAt));
    }

    @Override
    public void recordDelayedDispatch(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_DELAYED_REQUEUE_SCHEDULED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(), reason, occurredAt);
        record.setRoutingDecisionId(routingDecisionId);
        record.setNextAttemptAt(nextAttemptAt);
        record.setTaskDispatchAttemptNo(task == null ? null : task.getDispatchAttemptCount());
        append(record);
    }

    @Override
    public void recordRecoveryExhausted(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_RECOVERY_EXHAUSTED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(), reason, occurredAt);
        record.setRoutingDecisionId(routingDecisionId);
        record.setTaskDispatchAttemptNo(task == null ? null : task.getDispatchAttemptCount());
        append(record);
    }

    @Override
    public void recordRecoveryClaimed(TaskRecord task, String workerId, OffsetDateTime claimUntil, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_DELAYED_REQUEUE_CLAIMED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(), "Delayed dispatch recovery claimed by " + safe(workerId), occurredAt);
        record.setWorkerId(workerId);
        record.setClaimUntil(claimUntil);
        record.setTaskDispatchAttemptNo(task == null ? null : task.getDispatchAttemptCount());
        append(record);
    }

    @Override
    public void recordRecoveryScannerFailed(TaskRecord task, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_DELAYED_REQUEUE_FAILED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(), reason, occurredAt);
        record.setNextAttemptAt(nextAttemptAt);
        record.setTaskDispatchAttemptNo(task == null ? null : task.getDispatchAttemptCount() + 1);
        append(record);
    }

    @Override
    public void recordTaskReassigned(TaskRecord task, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_TASK_REASSIGNED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(), reason, occurredAt);
        record.setTaskDispatchAttemptNo(task == null ? null : task.getDispatchAttemptCount());
        append(record);
    }


    public DispatchAttemptHistoryRecord recordOperatorRuntimeBackoffCleared(AgentSnapshot agent, String operatorId, String reason, OffsetDateTime occurredAt) {
        return recordOperatorRuntimeBackoffCleared(agent, RecoveryOperatorAuditMetadata.basic(operatorId, "CLEAR_RUNTIME_BACKOFF"), reason, occurredAt);
    }

    public DispatchAttemptHistoryRecord recordOperatorRuntimeBackoffCleared(AgentSnapshot agent, RecoveryOperatorAuditMetadata audit, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = base(EVENT_OPERATOR_RUNTIME_BACKOFF_CLEARED, agent == null || agent.getStatus() == null ? null : agent.getStatus().name(),
                firstNonBlank(reason, "Runtime backoff cleared by operator"), occurredAt);
        if (agent != null) {
            record.setAgentId(agent.getAgentId());
            record.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
            record.setAgentSessionId(agent.getAgentSessionId());
            record.setSiteId(agent.getSiteId());
        }
        record.setWorkerId(operatorId(audit));
        Map<String, Object> payload = operatorPayload(audit, reason);
        payload.put("previousRuntimeFailureCount", agent == null ? null : agent.getRuntimeFailureCount());
        payload.put("clearedRuntimeBackoffUntil", agent == null ? null : agent.getRuntimeBackoffUntil());
        payload.put("clearedRuntimeBackoffReason", agent == null ? null : agent.getRuntimeBackoffReason());
        record.setPayloadJson(toJson(payload));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordOperatorRecoveryTriggered(TaskRecord task, String operatorId, String reason, OffsetDateTime occurredAt) {
        return recordOperatorRecoveryTriggered(task, RecoveryOperatorAuditMetadata.basic(operatorId, "TRIGGER_TASK_RECOVERY_NOW"), reason, occurredAt);
    }

    public DispatchAttemptHistoryRecord recordOperatorRecoveryTriggered(TaskRecord task, RecoveryOperatorAuditMetadata audit, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromTaskAndAssignment(task, null, EVENT_OPERATOR_RECOVERY_TRIGGERED,
                task == null || task.getStatus() == null ? null : task.getStatus().name(),
                firstNonBlank(reason, "Task delayed recovery triggered by operator"), occurredAt);
        record.setWorkerId(operatorId(audit));
        Map<String, Object> payload = operatorPayload(audit, reason);
        payload.put("previousNextDispatchAttemptAt", task == null ? null : task.getNextDispatchAttemptAt());
        payload.put("previousDispatchRetryReason", task == null ? null : task.getDispatchRetryReason());
        payload.put("previousDispatchAttemptCount", task == null ? null : task.getDispatchAttemptCount());
        record.setPayloadJson(toJson(payload));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordOperatorDeadLetterMoved(DispatchRequest request, String operatorId, String reason, OffsetDateTime occurredAt) {
        return recordOperatorDeadLetterMoved(request, RecoveryOperatorAuditMetadata.basic(operatorId, "MOVE_TO_DEAD_LETTER"), reason, occurredAt);
    }

    public DispatchAttemptHistoryRecord recordOperatorDeadLetterMoved(DispatchRequest request, RecoveryOperatorAuditMetadata audit, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_OPERATOR_DEAD_LETTER_MOVED, DispatchRequestStatus.DEAD_LETTER,
                firstNonBlank(reason, request == null ? null : request.getReason(), "Dispatch request moved to dead-letter by operator"), occurredAt);
        record.setWorkerId(operatorId(audit));
        record.setPayloadJson(toJson(operatorPayload(audit, reason)));
        return append(record);
    }

    public DispatchAttemptHistoryRecord recordOperatorDeadLetterRestored(DispatchRequest request, String operatorId, String reason, OffsetDateTime occurredAt) {
        return recordOperatorDeadLetterRestored(request, RecoveryOperatorAuditMetadata.basic(operatorId, "RESTORE_DEAD_LETTER"), reason, occurredAt);
    }

    public DispatchAttemptHistoryRecord recordOperatorDeadLetterRestored(DispatchRequest request, RecoveryOperatorAuditMetadata audit, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = fromRequest(request, EVENT_OPERATOR_DEAD_LETTER_RESTORED, request == null ? null : request.getStatus(),
                firstNonBlank(reason, request == null ? null : request.getReason(), "Dispatch request restored from dead-letter by operator"), occurredAt);
        record.setWorkerId(operatorId(audit));
        record.setNextAttemptAt(request == null ? null : request.getNextRetryAt());
        record.setPayloadJson(toJson(operatorPayload(audit, reason)));
        return append(record);
    }



    public DispatchAttemptHistoryRecord recordRecoveryApprovalEvent(RecoveryApprovalRequest approval, String eventType, RecoveryOperatorAuditMetadata audit, String reason, OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = base(eventType, approval == null || approval.getStatus() == null ? null : approval.getStatus().name(),
                firstNonBlank(reason, approval == null ? null : approval.getRequestReason(), "Recovery approval workflow event"), occurredAt);
        if (approval != null) {
            record.setTaskId(approval.getTaskId());
            record.setDispatchRequestId(approval.getDispatchRequestId());
            record.setAgentId(approval.getAgentId());
            record.setWorkerId(firstNonBlank(operatorId(audit), approval.getRequestedBy(), approval.getApprovedBy()));
            Map<String, Object> payload = operatorPayload(audit, reason);
            payload.put("approvalId", approval.getApprovalId());
            payload.put("approvalStatus", approval.getStatus() == null ? null : approval.getStatus().name());
            payload.put("approvalAction", approval.getAction());
            payload.put("targetType", approval.getTargetType());
            payload.put("targetId", approval.getTargetId());
            payload.put("requestedBy", approval.getRequestedBy());
            payload.put("requesterPrincipal", approval.getRequesterPrincipal());
            payload.put("approvedBy", approval.getApprovedBy());
            payload.put("approverPrincipal", approval.getApproverPrincipal());
            payload.put("expiresAt", approval.getExpiresAt());
            payload.put("executionResult", approval.getExecutionResult());
            payload.put("executionError", approval.getExecutionError());
            record.setPayloadJson(toJson(payload));
        }
        return append(record);
    }

    public java.util.List<DispatchAttemptHistoryRecord> findByTaskId(String taskId, int limit) {
        try {
            return repository.findByTaskId(taskId, limit);
        } catch (RuntimeException ex) {
            if (isMissingHistorySchema(ex)) {
                warnMissingSchemaOnce(ex);
                return List.of();
            }
            throw ex;
        }
    }

    public java.util.List<DispatchAttemptHistoryRecord> recent(int limit) {
        try {
            return repository.recent(limit);
        } catch (RuntimeException ex) {
            if (isMissingHistorySchema(ex)) {
                warnMissingSchemaOnce(ex);
                return List.of();
            }
            throw ex;
        }
    }

    public java.util.List<DispatchAttemptHistoryRecord> findSince(OffsetDateTime since, int limit) {
        try {
            return repository.findSince(since, limit);
        } catch (RuntimeException ex) {
            if (isMissingHistorySchema(ex)) {
                warnMissingSchemaOnce(ex);
                return List.of();
            }
            throw ex;
        }
    }

    public String mode() {
        return repository.mode();
    }

    private DispatchAttemptHistoryRecord fromRequest(
            DispatchRequest request,
            String eventType,
            DispatchRequestStatus status,
            String reason,
            OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = base(eventType, status == null ? null : status.name(), reason, occurredAt);
        if (request != null) {
            record.setTaskId(request.getTaskId());
            record.setIncidentId(request.getIncidentId());
            record.setAssignmentId(request.getAssignmentId());
            record.setDispatchRequestId(request.getDispatchRequestId());
            record.setAgentId(request.getAgentId());
            record.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
            record.setAgentSessionId(request.getAgentSessionId());
            record.setSiteId(request.getSiteId());
            record.setAttemptNo(request.getAttemptCount());
            record.setErrorMessage(request.getLastError());
        }
        return record;
    }

    private DispatchAttemptHistoryRecord fromTaskAndAssignment(
            TaskRecord task,
            TaskAssignment assignment,
            String eventType,
            String status,
            String reason,
            OffsetDateTime occurredAt) {
        DispatchAttemptHistoryRecord record = base(eventType, status, reason, occurredAt);
        if (task != null) {
            record.setTaskId(task.getTaskId());
            record.setIncidentId(task.getIncidentId());
            record.setSiteId(task.getSiteId());
            record.setTaskDispatchAttemptNo(task.getDispatchAttemptCount());
            record.setNextAttemptAt(task.getNextDispatchAttemptAt());
        }
        if (assignment != null) {
            record.setAssignmentId(assignment.getAssignmentId());
            record.setAgentId(assignment.getAgentId());
            record.setOwnerGatewayNodeId(assignment.getOwnerGatewayNodeId());
            record.setAgentSessionId(assignment.getAgentSessionId());
            record.setSiteId(assignment.getSiteId());
            record.setRoutingDecisionId(assignment.getRoutingDecisionId());
        }
        return record;
    }

    private DispatchAttemptHistoryRecord base(String eventType, String status, String reason, OffsetDateTime occurredAt) {
        OffsetDateTime at = occurredAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : occurredAt;
        DispatchAttemptHistoryRecord record = new DispatchAttemptHistoryRecord();
        record.setHistoryId("dispatch-history-" + UUID.randomUUID());
        record.setEventType(eventType);
        record.setStatus(status);
        record.setReason(reason);
        record.setOccurredAt(at);
        record.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setPayloadJson("{}");
        return record;
    }

    private DispatchAttemptHistoryRecord append(DispatchAttemptHistoryRecord record) {
        try {
            DispatchAttemptHistoryRecord saved = repository.append(record);
            recoveryMetricsPort.recordDispatchRecoveryEvent(saved);
            return saved;
        } catch (RuntimeException ex) {
            if (isMissingHistorySchema(ex)) {
                warnMissingSchemaOnce(ex);
                return record;
            }
            throw ex;
        }
    }

    private boolean isMissingHistorySchema(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("does not exist")
                        && (normalized.contains("dispatch_attempt_history")
                        || normalized.contains("idx_dispatch_attempt_history"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void warnMissingSchemaOnce(Throwable exception) {
        if (missingSchemaWarningLogged.compareAndSet(false, true)) {
            log.warn("Dispatch attempt history schema is not ready; history will be temporarily degraded. Run Flyway V36/V39 or manually create dispatch_attempt_history. Root cause: {}", rootMessage(exception));
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "unknown" : (current.getMessage() == null ? current.getClass().getName() : current.getMessage());
    }

    private Map<String, Object> gatewayPayload(GatewayDispatchResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result != null) {
            payload.put("httpStatus", result.httpStatus());
            payload.put("gatewayStatus", result.gatewayStatus());
            payload.put("message", result.message());
            payload.put("success", result.success());
        }
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(sanitizeForAudit(value == null ? Map.of() : value));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Object sanitizeForAudit(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object entryValue = entry.getValue();
                if (isSensitiveAuditKey(key)) {
                    sanitized.put(key + "Present", entryValue != null && !String.valueOf(entryValue).isBlank());
                    sanitized.put(key + "Fingerprint", fingerprint(entryValue == null ? null : String.valueOf(entryValue)));
                } else {
                    sanitized.put(key, sanitizeForAudit(entryValue));
                }
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeForAudit).toList();
        }
        return value;
    }

    private boolean isSensitiveAuditKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("dispatchtoken")
                || normalized.endsWith("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("credential")
                || normalized.equals("authorization")
                || normalized.equals("cookie");
    }

    private String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception ex) {
            return "sha256:unavailable";
        }
    }

    private Map<String, Object> operatorPayload(String operatorId, String reason) {
        return operatorPayload(RecoveryOperatorAuditMetadata.basic(operatorId, "LEGACY_OPERATOR_ACTION"), reason);
    }

    private Map<String, Object> operatorPayload(RecoveryOperatorAuditMetadata audit, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operatorId", operatorId(audit));
        payload.put("principal", audit == null ? null : safe(audit.principal()));
        payload.put("role", audit == null ? null : safe(audit.role()));
        payload.put("action", audit == null ? null : safe(audit.action()));
        payload.put("riskLevel", audit == null ? null : safe(audit.riskLevel()));
        payload.put("requestId", audit == null ? null : safe(audit.requestId()));
        payload.put("clientAddress", audit == null ? null : safe(audit.clientAddress()));
        payload.put("userAgent", audit == null ? null : safe(audit.userAgent()));
        payload.put("confirmationPolicy", audit == null ? null : safe(audit.confirmationPolicy()));
        payload.put("reason", safe(reason));
        return payload;
    }

    private String operatorId(RecoveryOperatorAuditMetadata audit) {
        return firstNonBlank(audit == null ? null : audit.operatorId(), audit == null ? null : audit.principal(), "unknown-operator");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
