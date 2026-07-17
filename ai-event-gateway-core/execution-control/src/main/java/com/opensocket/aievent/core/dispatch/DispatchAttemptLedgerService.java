package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackType;

/**
 * Durable query view for dispatch attempts.
 *
 * <p>The ledger intentionally derives from Core persisted dispatch rows and persisted callback
 * records, not Gateway node memory. Gateway telemetry can enrich diagnostics, but this service is
 * the authority used by Admin UI and later recovery processors when topology changes from single to
 * cluster, cluster to single, or after a Gateway node restart.</p>
 */
@Service
public class DispatchAttemptLedgerService {
    public static final String SOURCE_CORE_DISPATCH = "CORE_DISPATCH";
    public static final String SOURCE_CALLBACK_INBOX = "CALLBACK_INBOX";

    private final DispatchRequestRepository dispatchRepository;
    private final TaskCallbackRepository callbackRepository;

    public DispatchAttemptLedgerService(DispatchRequestRepository dispatchRepository,
                                        TaskCallbackRepository callbackRepository) {
        this.dispatchRepository = dispatchRepository;
        this.callbackRepository = callbackRepository;
    }

    public List<DispatchAttemptLedger> findByTaskId(String taskId, int limit) {
        int capped = safeLimit(limit);
        List<DispatchRequest> dispatches = dispatchRepository.findByTaskId(taskId, capped);
        List<TaskCallbackRecord> taskScopedFallback = callbackRepository.findByTaskId(taskId, Math.max(capped * 10, 100));
        return dispatches.stream()
                .sorted(dispatchComparator())
                .map(dispatch -> build(dispatch, callbacksForDispatch(dispatch, capped, taskScopedFallback), capped))
                .toList();
    }

    public Optional<DispatchAttemptLedger> findByDispatchRequestId(String dispatchRequestId, int limit) {
        int capped = safeLimit(limit);
        return dispatchRepository.findById(dispatchRequestId)
                .map(dispatch -> build(dispatch, callbacksForDispatch(dispatch, capped, List.of()), capped));
    }


    private List<TaskCallbackRecord> callbacksForDispatch(DispatchRequest dispatch, int limit, List<TaskCallbackRecord> taskScopedFallback) {
        if (dispatch == null || dispatch.getDispatchRequestId() == null || dispatch.getDispatchRequestId().isBlank()) {
            return List.of();
        }
        List<TaskCallbackRecord> exact = callbackRepository.findByDispatchRequestId(dispatch.getDispatchRequestId(), Math.max(safeLimit(limit) * 5, 100));
        if (!exact.isEmpty()) {
            return exact;
        }
        return matchingCallbacks(dispatch, taskScopedFallback);
    }

    private DispatchAttemptLedger build(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks, int limit) {
        List<TaskCallbackRecord> matchedCallbacks = matchingCallbacks(dispatch, callbacks).stream()
                .sorted(callbackComparator())
                .limit(safeLimit(limit))
                .toList();

        DispatchAttemptLedger ledger = new DispatchAttemptLedger();
        ledger.setDispatchRequestId(dispatch.getDispatchRequestId());
        ledger.setTaskId(dispatch.getTaskId());
        ledger.setIncidentId(dispatch.getIncidentId());
        ledger.setAssignmentId(dispatch.getAssignmentId());
        ledger.setAgentId(dispatch.getAgentId());
        ledger.setLastKnownGatewayNodeId(firstNonBlank(dispatch.getOwnerGatewayNodeId(), latestGatewayNode(matchedCallbacks)));
        ledger.setLastKnownAgentSessionId(firstNonBlank(dispatch.getAgentSessionId(), latestAgentSession(matchedCallbacks)));
        ledger.setDispatchStatus(name(dispatch.getStatus()));
        ledger.setDeliveryState(deliveryState(dispatch));
        ledger.setAttemptNo(dispatch.getAttemptCount());
        ledger.setLastCallbackId(firstNonBlank(dispatch.getLastCallbackId(), latestCallbackId(matchedCallbacks)));
        ledger.setDispatchTokenPresent(dispatch.getDispatchToken() != null && !dispatch.getDispatchToken().isBlank());
        ledger.setCreatedAt(dispatch.getCreatedAt());
        ledger.setUpdatedAt(dispatch.getUpdatedAt());
        ledger.setDispatchedAt(dispatch.getDispatchedAt());
        ledger.setLeaseExpiresAt(dispatch.getClaimUntil());
        ledger.setLastError(dispatch.getLastError());
        ledger.setCallbackState(callbackState(matchedCallbacks));
        ledger.setResultState(resultState(dispatch, matchedCallbacks));
        ledger.setAckReceivedAt(firstAcceptedAt(matchedCallbacks, TaskCallbackType.ACK));
        ledger.setProgressReceivedAt(firstAcceptedAt(matchedCallbacks, TaskCallbackType.PROGRESS));
        ledger.setResultReceivedAt(firstAcceptedAt(matchedCallbacks, TaskCallbackType.RESULT));
        ledger.setErrorReceivedAt(firstAcceptedAt(matchedCallbacks, TaskCallbackType.ERROR));
        ledger.setTerminalAt(firstNonNull(dispatch.getCompletedAt(), dispatch.getFailedAt(), dispatch.getTimedOutAt(), dispatch.getDeadLetterAt()));
        ledger.setCallbackDeadlineAt(null);
        ledger.setRecoveryRequired(recoveryRequired(dispatch, matchedCallbacks));
        ledger.setNextAction(nextAction(dispatch, matchedCallbacks));
        ledger.setEvents(events(dispatch, matchedCallbacks, limit));
        return ledger;
    }

    private List<DispatchAttemptLedgerEvent> events(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks, int limit) {
        List<DispatchAttemptLedgerEvent> events = new ArrayList<>();
        events.add(dispatchEvent(dispatch));
        for (TaskCallbackRecord callback : callbacks) {
            events.add(callbackEvent(callback));
        }
        return events.stream()
                .sorted(Comparator.comparing(DispatchAttemptLedgerEvent::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit(limit))
                .toList();
    }

    private DispatchAttemptLedgerEvent dispatchEvent(DispatchRequest dispatch) {
        DispatchAttemptLedgerEvent event = new DispatchAttemptLedgerEvent();
        event.setEventId("dispatch-" + dispatch.getDispatchRequestId());
        event.setEventType("DISPATCH_" + name(dispatch.getStatus()));
        event.setSource(SOURCE_CORE_DISPATCH);
        event.setStatus(name(dispatch.getStatus()));
        event.setTaskId(dispatch.getTaskId());
        event.setDispatchRequestId(dispatch.getDispatchRequestId());
        event.setAgentId(dispatch.getAgentId());
        event.setOwnerGatewayNodeId(dispatch.getOwnerGatewayNodeId());
        event.setAgentSessionId(dispatch.getAgentSessionId());
        event.setAttemptNo(dispatch.getAttemptCount());
        event.setReason(dispatch.getReason());
        event.setErrorMessage(dispatch.getLastError());
        event.setAuthoritative(true);
        event.setOccurredAt(firstNonNull(dispatch.getUpdatedAt(), dispatch.getDispatchedAt(), dispatch.getCreatedAt()));
        return event;
    }

    private DispatchAttemptLedgerEvent callbackEvent(TaskCallbackRecord callback) {
        DispatchAttemptLedgerEvent event = new DispatchAttemptLedgerEvent();
        event.setEventId("callback-" + callback.getCallbackId());
        event.setEventType("CALLBACK_" + name(callback.getCallbackType()));
        event.setSource(SOURCE_CALLBACK_INBOX);
        event.setStatus(callback.isAccepted() ? "ACCEPTED" : "REJECTED");
        event.setTaskId(callback.getTaskId());
        event.setDispatchRequestId(callback.getDispatchRequestId());
        event.setCallbackId(callback.getCallbackId());
        event.setAgentId(callback.getAgentId());
        event.setOwnerGatewayNodeId(callback.getOwnerGatewayNodeId());
        event.setAgentSessionId(callback.getAgentSessionId());
        event.setAttemptNo(callback.getAttemptNo());
        event.setIdempotencyKey(callback.getIdempotencyKey());
        event.setReason(callback.isAccepted() ? callback.getMessage() : callback.getIgnoredReason());
        event.setErrorCode(callback.getErrorCode());
        event.setErrorMessage(callback.getErrorMessage());
        event.setAuthoritative(true);
        event.setOccurredAt(firstNonNull(callback.getProcessedAt(), callback.getOccurredAt()));
        return event;
    }

    private List<TaskCallbackRecord> matchingCallbacks(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks) {
        if (dispatch == null || callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        String dispatchRequestId = dispatch.getDispatchRequestId();
        String taskId = dispatch.getTaskId();
        List<TaskCallbackRecord> exact = callbacks.stream()
                .filter(callback -> dispatchRequestId != null && dispatchRequestId.equals(callback.getDispatchRequestId()))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return callbacks.stream()
                .filter(callback -> taskId != null && taskId.equals(callback.getTaskId()))
                .toList();
    }

    private String deliveryState(DispatchRequest dispatch) {
        String status = name(dispatch.getStatus());
        if (status == null || status.isBlank()) return "UNKNOWN";
        return switch (status) {
            case "PENDING_REVIEW", "APPROVED" -> "NOT_DELIVERED";
            case "DISPATCHING" -> "DELIVERING";
            case "DISPATCHED", "ACKED", "RUNNING", "COMPLETED" -> "DELIVERED_TO_GATEWAY";
            case "RETRY_WAITING" -> "RETRY_WAIT";
            case "FAILED", "DEAD_LETTER", "TIMED_OUT", "TIMEOUT", "CANCELLED" -> "DELIVERY_FAILED";
            default -> status;
        };
    }

    private String callbackState(List<TaskCallbackRecord> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) return "NO_CALLBACK";
        if (callbacks.stream().anyMatch(callback -> !callback.isAccepted())) return "CALLBACK_REJECTED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.ERROR && callback.isAccepted())) return "ERROR_RECEIVED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.RESULT && callback.isAccepted())) return "RESULT_RECEIVED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.PROGRESS && callback.isAccepted())) return "PROGRESS_RECEIVED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.ACK && callback.isAccepted())) return "ACK_RECEIVED";
        return "CALLBACK_RECEIVED";
    }

    private String resultState(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks) {
        String status = name(dispatch.getStatus());
        if ("COMPLETED".equals(status)) return "SUCCEEDED";
        if ("FAILED".equals(status) || "DEAD_LETTER".equals(status) || "TIMED_OUT".equals(status) || "TIMEOUT".equals(status)) return "FAILED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.ERROR && callback.isAccepted())) return "FAILED";
        if (callbacks.stream().anyMatch(callback -> callback.getCallbackType() == TaskCallbackType.RESULT && callback.isAccepted())) return "RESULT_RECEIVED";
        return "PENDING";
    }

    private boolean recoveryRequired(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks) {
        String status = name(dispatch.getStatus());
        if ("FAILED".equals(status) || "DEAD_LETTER".equals(status) || "TIMED_OUT".equals(status) || "TIMEOUT".equals(status)) return true;
        return callbacks.stream().anyMatch(callback -> !callback.isAccepted());
    }

    private String nextAction(DispatchRequest dispatch, List<TaskCallbackRecord> callbacks) {
        String status = name(dispatch.getStatus());
        if (callbacks.stream().anyMatch(callback -> !callback.isAccepted())) return "INSPECT_CALLBACK_REJECTION";
        if ("APPROVED".equals(status)) return "WAIT_FOR_DISPATCH_WORKER";
        if ("DISPATCHING".equals(status)) return "WAIT_FOR_GATEWAY_DELIVERY";
        if ("DISPATCHED".equals(status)) return "WAIT_FOR_AGENT_ACK_OR_RESULT";
        if ("ACKED".equals(status) || "RUNNING".equals(status)) return "WAIT_FOR_AGENT_RESULT";
        if ("RETRY_WAITING".equals(status)) return "WAIT_FOR_RETRY_OR_RECOVERY";
        if ("FAILED".equals(status) || "DEAD_LETTER".equals(status) || "TIMED_OUT".equals(status) || "TIMEOUT".equals(status)) return "RUN_RECOVERY_DECISION";
        if ("COMPLETED".equals(status)) return "NONE";
        return "INSPECT_DISPATCH_LEDGER";
    }

    private OffsetDateTime firstAcceptedAt(List<TaskCallbackRecord> callbacks, TaskCallbackType type) {
        return callbacks.stream()
                .filter(callback -> callback.getCallbackType() == type && callback.isAccepted())
                .map(callback -> firstNonNull(callback.getProcessedAt(), callback.getOccurredAt()))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private String latestCallbackId(List<TaskCallbackRecord> callbacks) {
        return callbacks.stream()
                .sorted(callbackComparator())
                .map(TaskCallbackRecord::getCallbackId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String latestGatewayNode(List<TaskCallbackRecord> callbacks) {
        return callbacks.stream()
                .sorted(callbackComparator())
                .map(TaskCallbackRecord::getOwnerGatewayNodeId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String latestAgentSession(List<TaskCallbackRecord> callbacks) {
        return callbacks.stream()
                .sorted(callbackComparator())
                .map(TaskCallbackRecord::getAgentSessionId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Comparator<DispatchRequest> dispatchComparator() {
        return Comparator.comparing((DispatchRequest request) -> firstNonNull(request.getUpdatedAt(), request.getCreatedAt()), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<TaskCallbackRecord> callbackComparator() {
        return Comparator.comparing((TaskCallbackRecord callback) -> firstNonNull(callback.getProcessedAt(), callback.getOccurredAt()), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String name(TaskCallbackType value) {
        return value == null ? null : value.name();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }
}
