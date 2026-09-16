package com.opensocket.aievent.core.a2a.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2ACancellationRepository;
import com.opensocket.aievent.core.a2a.A2AResultAcceptanceDecision;
import com.opensocket.aievent.core.a2a.A2AResultClassification;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;
import com.opensocket.aievent.core.a2a.core.port.ResultEvidenceAuthorityPort;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttempt;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptRepository;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptStatus;

/**
 * Read-only bridge to Assignment, Execution Attempt, Dispatch and Callback authorities.
 * It validates fingerprints against authority-owned raw secrets but never returns or
 * persists those raw secrets in the A2A bounded context.
 */
@Component
public class DefaultA2AResultEvidenceAuthorityAdapter implements ResultEvidenceAuthorityPort {
    private final DispatchRequestRepository dispatches;
    private final TaskAssignmentRepository assignments;
    private final TaskExecutionAttemptRepository attempts;
    private final TaskCallbackRepository callbacks;
    private final A2ACancellationRepository cancellations;

    public DefaultA2AResultEvidenceAuthorityAdapter(
            DispatchRequestRepository dispatches,
            TaskAssignmentRepository assignments,
            TaskExecutionAttemptRepository attempts,
            TaskCallbackRepository callbacks,
            A2ACancellationRepository cancellations) {
        this.dispatches = dispatches;
        this.assignments = assignments;
        this.attempts = attempts;
        this.callbacks = callbacks;
        this.cancellations = cancellations;
    }

    @Override
    public Verification verify(String childTaskId, A2AResultSubmission submission) {
        if (submission == null || blank(childTaskId)) {
            return reject(A2AResultAcceptanceDecision.MISSING_EVIDENCE,
                    "A2A_RESULT_CONTEXT_MISSING", "Child Task and submission are required", null, null, null, null, null, null, null);
        }
        A2ACancellationRecord cancellation = cancellations
                .findByRequest(submission.tenantId(), submission.requestId()).orElse(null);
        TaskCallbackRecord callback = callbacks.findByCallbackId(submission.callbackInboxId()).orElse(null);
        boolean cancelCompleteRaceWinner = cancellation != null
                && callback != null && callback.isAccepted() && callback.getOccurredAt() != null
                && cancellation.getResultCutoffAt() != null
                && !callback.getOccurredAt().isAfter(cancellation.getResultCutoffAt())
                && same(callback.getAssignmentId(), cancellation.getAssignmentId())
                && same(submission.assignmentId(), cancellation.getAssignmentId())
                && (blank(cancellation.getExecutionAttemptId())
                    || same(submission.executionAttemptId(), cancellation.getExecutionAttemptId()))
                && (cancellation.getAttemptNo() == null
                    || same(callback.getAttemptNo(), cancellation.getAttemptNo()))
                && same(secretFingerprint(callback.getFencingToken()),
                        cancellation.getRevokedFencingTokenHash());
        DispatchRequest dispatch = dispatches.findById(submission.dispatchRequestId()).orElse(null);
        if (dispatch == null) {
            return reject(A2AResultAcceptanceDecision.MISSING_EVIDENCE,
                    "DISPATCH_REQUEST_NOT_FOUND", "Dispatch Request evidence was not found", null, null, null, null, null, null, null);
        }
        if (!same(childTaskId, dispatch.getTaskId()) || !same(submission.assignmentId(), dispatch.getAssignmentId())) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "DISPATCH_BINDING_CONFLICT", "Dispatch Request is not bound to the A2A Child Task and Assignment",
                    dispatch.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    dispatch.getAgentId(), dispatch.getAgentSessionId(), null);
        }
        if (revoked(dispatch.getStatus()) && !cancelCompleteRaceWinner) {
            return reject(A2AResultAcceptanceDecision.REVOKED,
                    "DISPATCH_REVOKED", "Dispatch Request is no longer authorized to produce a result",
                    dispatch.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    dispatch.getAgentId(), dispatch.getAgentSessionId(), null);
        }
        if (!same(submission.dispatchTokenHash(), secretFingerprint(dispatch.getDispatchToken()))) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "DISPATCH_TOKEN_STALE", "Dispatch token fingerprint does not match the authoritative Dispatch Request",
                    dispatch.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    dispatch.getAgentId(), dispatch.getAgentSessionId(), null);
        }
        if (submission.attemptNo() == null || submission.attemptNo() != dispatch.getAttemptCount()) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "DISPATCH_ATTEMPT_STALE", "Callback attempt number is not the current Dispatch attempt",
                    dispatch.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    dispatch.getAgentId(), dispatch.getAgentSessionId(), null);
        }

        TaskAssignment assignment = assignments.findById(dispatch.getAssignmentId()).orElse(null);
        if (assignment == null) {
            return reject(A2AResultAcceptanceDecision.MISSING_EVIDENCE,
                    "ASSIGNMENT_NOT_FOUND", "Assignment evidence was not found",
                    dispatch.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    dispatch.getAgentId(), dispatch.getAgentSessionId(), null);
        }
        if (!same(submission.tenantId(), assignment.getTenantId()) || !same(childTaskId, assignment.getTaskId())) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "ASSIGNMENT_BINDING_CONFLICT", "Assignment tenant or Task binding conflicts with the A2A Request",
                    assignment.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (assignment.getStatus() == AssignmentStatus.CANCELLED && !cancelCompleteRaceWinner) {
            return reject(A2AResultAcceptanceDecision.REVOKED,
                    "ASSIGNMENT_REVOKED", "Assignment has been cancelled",
                    assignment.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!same(submission.completedByAgentId(), assignment.getAgentId())
                || !same(submission.agentSessionId(), assignment.getAgentSessionId())) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "AGENT_SESSION_CONFLICT", "Agent or Agent Session is not the authoritative assignee",
                    assignment.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!cancelCompleteRaceWinner
                && !same(submission.fencingTokenHash(), secretFingerprint(assignment.getFencingToken()))) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "FENCING_TOKEN_STALE", "Fencing token fingerprint is stale",
                    assignment.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }

        TaskExecutionAttempt execution = blank(submission.executionAttemptId())
                ? attempts.findLatestByAssignmentId(assignment.getAssignmentId()).orElse(null)
                : attempts.findById(submission.executionAttemptId()).orElse(null);
        if (execution == null) {
            return reject(A2AResultAcceptanceDecision.MISSING_EVIDENCE,
                    "EXECUTION_ATTEMPT_NOT_FOUND", "Execution Attempt evidence was not found",
                    assignment.getAssignmentId(), null, dispatch.getAttemptCount(), dispatch.getDispatchRequestId(),
                    assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!blank(submission.executionAttemptId())
                && !same(submission.executionAttemptId(), execution.getExecutionAttemptId())) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "EXECUTION_ATTEMPT_STALE", "Submitted Execution Attempt is not current",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!same(childTaskId, execution.getTaskId()) || !same(assignment.getAssignmentId(), execution.getAssignmentId())
                || execution.getAttemptNo() != submission.attemptNo()) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "EXECUTION_BINDING_CONFLICT", "Execution Attempt binding conflicts with callback evidence",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!cancelCompleteRaceWinner && (execution.getStatus() == TaskExecutionAttemptStatus.CANCELLED
                || execution.getStatus() == TaskExecutionAttemptStatus.STALE_CALLBACK_REJECTED)) {
            return reject(A2AResultAcceptanceDecision.REVOKED,
                    "EXECUTION_ATTEMPT_REVOKED", "Execution Attempt cannot produce a canonical result",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!cancelCompleteRaceWinner
                && !same(submission.fencingTokenHash(), secretFingerprint(execution.getFencingToken()))) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "EXECUTION_FENCING_STALE", "Execution Attempt fencing fingerprint is stale",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }

        if (callback == null) {
            return reject(A2AResultAcceptanceDecision.MISSING_EVIDENCE,
                    "CALLBACK_INBOX_NOT_FOUND", "Accepted Callback Inbox evidence was not found",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(), null);
        }
        if (!callback.isAccepted()) {
            return reject(A2AResultAcceptanceDecision.STALE,
                    "CALLBACK_NOT_ACCEPTED", "Callback Inbox rejected the callback: " + callback.getIgnoredReason(),
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(),
                    callback.getCallbackFingerprint());
        }
        if (!same(callback.getTaskId(), childTaskId)
                || !same(callback.getDispatchRequestId(), dispatch.getDispatchRequestId())
                || !same(callback.getAssignmentId(), assignment.getAssignmentId())
                || !same(callback.getAgentId(), assignment.getAgentId())
                || !same(callback.getAgentSessionId(), assignment.getAgentSessionId())
                || callback.getAttemptNo() == null || callback.getAttemptNo() != execution.getAttemptNo()) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "CALLBACK_BINDING_CONFLICT", "Callback Inbox identity does not match authoritative execution evidence",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(),
                    callback.getCallbackFingerprint());
        }
        if (!same(submission.payloadHash(), payloadHash(callback.getPayload()))) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "CALLBACK_PAYLOAD_CONFLICT", "Payload hash differs from the accepted Callback Inbox payload",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(),
                    callback.getCallbackFingerprint());
        }
        if (!resultStatusMatchesCallback(submission, callback)) {
            return reject(A2AResultAcceptanceDecision.CONFLICT,
                    "CALLBACK_RESULT_STATUS_CONFLICT",
                    "Submitted A2A Result status does not match the accepted Callback Task transition",
                    assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                    dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(),
                    callback.getCallbackFingerprint());
        }
        return new Verification(A2AResultAcceptanceDecision.ACCEPTED, null,
                cancelCompleteRaceWinner ? "AGENT_ALREADY_COMPLETED_BEFORE_CUTOFF"
                        : "A2A_RESULT_EVIDENCE_ACCEPTED",
                cancelCompleteRaceWinner
                        ? "Canonical callback occurred before cancellation fencing cutoff"
                        : "Assignment, Dispatch, Execution Attempt and Callback Inbox evidence verified",
                assignment.getAssignmentId(), execution.getExecutionAttemptId(), execution.getAttemptNo(),
                dispatch.getDispatchRequestId(), assignment.getAgentId(), assignment.getAgentSessionId(),
                callback.getCallbackFingerprint());
    }

    private boolean resultStatusMatchesCallback(A2AResultSubmission submission, TaskCallbackRecord callback) {
        if (submission.resultStatus() == null || blank(callback.getNewTaskStatus())) return false;
        return switch (submission.resultStatus()) {
            case SUCCEEDED -> "COMPLETED".equals(callback.getNewTaskStatus());
            case PARTIAL -> "PARTIALLY_COMPLETED".equals(callback.getNewTaskStatus());
            case FAILED -> "FAILED".equals(callback.getNewTaskStatus());
            case CANCELLED -> "CANCELLED".equals(callback.getNewTaskStatus());
        };
    }

    private Verification reject(A2AResultAcceptanceDecision decision, String code, String reason,
            String assignmentId, String executionAttemptId, Integer attemptNo, String dispatchRequestId,
            String agentId, String sessionId, String fingerprint) {
        return new Verification(decision, classification(code), code, reason, assignmentId, executionAttemptId, attemptNo,
                dispatchRequestId, agentId, sessionId, fingerprint);
    }


    private A2AResultClassification classification(String code) {
        if (code == null) return A2AResultClassification.MISSING_EVIDENCE;
        if (code.contains("ASSIGNMENT_NOT_FOUND")) return A2AResultClassification.UNKNOWN_ASSIGNMENT;
        if (code.contains("REVOKED") || code.contains("CANCELLED")) return A2AResultClassification.LATE_RESULT;
        if (code.contains("TOKEN") || code.contains("FENCING")) return A2AResultClassification.TOKEN_MISMATCH;
        if (code.contains("ATTEMPT") || code.contains("STALE")) return A2AResultClassification.STALE_ATTEMPT;
        if (code.contains("NOT_FOUND") || code.contains("MISSING")) return A2AResultClassification.MISSING_EVIDENCE;
        return A2AResultClassification.BINDING_CONFLICT;
    }

    private boolean revoked(DispatchRequestStatus status) {
        return status == DispatchRequestStatus.CANCELLED || status == DispatchRequestStatus.REJECTED
                || status == DispatchRequestStatus.SUPPRESSED || status == DispatchRequestStatus.TIMED_OUT
                || status == DispatchRequestStatus.DEAD_LETTER;
    }

    private String secretFingerprint(String secret) { return blank(secret) ? "" : sha256("sec-", secret); }
    private String payloadHash(Map<String,Object> payload) { return sha256("pay-", canonical(payload)); }
    private String sha256(String prefix, String value) {
        try { return prefix + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { return prefix + Math.abs(value.hashCode()); }
    }
    private String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?,?> map) return map.entrySet().stream()
                .sorted((a,b)->String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())))
                .map(e->String.valueOf(e.getKey())+"="+canonical(e.getValue())).toList().toString();
        if (value instanceof Iterable<?> values) {
            StringBuilder b=new StringBuilder("["); boolean first=true;
            for(Object item:values){if(!first)b.append(',');b.append(canonical(item));first=false;}
            return b.append(']').toString();
        }
        return String.valueOf(value);
    }
    private boolean same(Object a,Object b){return Objects.equals(a,b);}
    private boolean blank(String value){return value==null||value.isBlank();}
}
