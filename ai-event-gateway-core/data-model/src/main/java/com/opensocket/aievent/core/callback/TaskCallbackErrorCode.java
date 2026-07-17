package com.opensocket.aievent.core.callback;

/**
 * Stable callback error contract used by Netty and Agent side retry logic.
 */
public enum TaskCallbackErrorCode {
    NONE(200, false, "Callback accepted"),
    DUPLICATE_CALLBACK(200, false, "Duplicate callback was ignored idempotently"),
    CALLBACK_REPLAY_MISMATCH(409, false, "Callback id was replayed with a different payload fingerprint"),
    BAD_REQUEST(400, false, "Malformed callback request"),
    MISSING_DISPATCH_REQUEST(400, true, "dispatchRequestId is required or no active dispatch can be resolved"),
    TASK_NOT_FOUND(404, false, "Task does not exist"),
    DISPATCH_NOT_FOUND(404, true, "Dispatch request does not exist"),
    DISPATCH_TOKEN_NOT_ISSUED(409, true, "Dispatch token has not been issued yet"),
    DISPATCH_TOKEN_REQUIRED(401, false, "Dispatch token is required"),
    INVALID_DISPATCH_TOKEN(403, false, "Dispatch token is invalid"),
    ATTEMPT_NO_REQUIRED(400, false, "Callback attempt number is required"),
    OLD_ATTEMPT_CALLBACK(409, false, "Callback belongs to an older dispatch attempt"),
    FUTURE_ATTEMPT_CALLBACK(409, true, "Callback belongs to a future dispatch attempt"),
    IDENTITY_REQUIRED(400, false, "Gateway, agent, or session identity is required"),
    IDENTITY_MISMATCH(403, false, "Gateway, agent, or session identity does not match dispatch contract"),
    ASSIGNMENT_ID_REQUIRED(400, false, "assignmentId is required for fenced callbacks"),
    ASSIGNMENT_NOT_FOUND(404, true, "Assignment does not exist or cannot be loaded for fencing validation"),
    FENCING_TOKEN_REQUIRED(401, false, "Assignment fencing token is required"),
    INVALID_FENCING_TOKEN(409, false, "Assignment fencing token is stale or invalid"),
    ASSIGNMENT_LEASE_EXPIRED(409, false, "Assignment lease has expired"),
    TASK_ALREADY_TERMINAL(409, false, "Task is already terminal"),
    DISPATCH_ALREADY_TERMINAL(409, false, "Dispatch request is already terminal"),
    INVALID_STATE_TRANSITION(409, false, "Callback state transition is invalid"),
    CONCURRENT_STATE_CONFLICT(409, true, "Callback lost the atomic state transition race"),
    INTERNAL_ERROR(500, true, "Internal callback processing error");

    private final int httpStatus;
    private final boolean retryable;
    private final String description;

    TaskCallbackErrorCode(int httpStatus, boolean retryable, String description) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.description = description;
    }

    public int getHttpStatus() { return httpStatus; }
    public boolean isRetryable() { return retryable; }
    public String getDescription() { return description; }

    public static TaskCallbackErrorCode fromIgnoredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return NONE;
        }
        String normalized = reason.trim().toUpperCase();
        if (normalized.startsWith("INVALID_DISPATCH_TRANSITION_")) return INVALID_STATE_TRANSITION;

        // Exact protocol-level reasons must be mapped before generic suffix buckets.
        // CALLBACK_REPLAY_MISMATCH also ends with _MISMATCH, but it is not an
        // agent/gateway/session identity error; Netty and agent retry logic need
        // the stable replay-specific contract.
        if ("CALLBACK_REPLAY_MISMATCH".equals(normalized)) return CALLBACK_REPLAY_MISMATCH;

        if (normalized.endsWith("_REQUIRED") && (normalized.contains("AGENTID") || normalized.contains("OWNERGATEWAYNODEID") || normalized.contains("AGENTSESSIONID"))) return IDENTITY_REQUIRED;
        if (normalized.endsWith("_MISMATCH") && !normalized.startsWith("ASSIGNMENT_ID")) return IDENTITY_MISMATCH;
        if (normalized.startsWith("EXPECTED_") && normalized.endsWith("_MISSING")) return IDENTITY_REQUIRED;
        return switch (normalized) {
            case "MISSING_DISPATCH_REQUEST" -> MISSING_DISPATCH_REQUEST;
            case "TASK_NOT_FOUND" -> TASK_NOT_FOUND;
            case "DISPATCH_NOT_FOUND" -> DISPATCH_NOT_FOUND;
            case "DISPATCH_TOKEN_NOT_ISSUED" -> DISPATCH_TOKEN_NOT_ISSUED;
            case "DISPATCH_TOKEN_REQUIRED" -> DISPATCH_TOKEN_REQUIRED;
            case "INVALID_DISPATCH_TOKEN" -> INVALID_DISPATCH_TOKEN;
            case "ATTEMPT_NO_REQUIRED" -> ATTEMPT_NO_REQUIRED;
            case "OLD_ATTEMPT_CALLBACK" -> OLD_ATTEMPT_CALLBACK;
            case "FUTURE_ATTEMPT_CALLBACK" -> FUTURE_ATTEMPT_CALLBACK;
            case "ASSIGNMENT_ID_REQUIRED" -> ASSIGNMENT_ID_REQUIRED;
            case "ASSIGNMENT_NOT_FOUND" -> ASSIGNMENT_NOT_FOUND;
            case "FENCING_TOKEN_REQUIRED" -> FENCING_TOKEN_REQUIRED;
            case "INVALID_FENCING_TOKEN" -> INVALID_FENCING_TOKEN;
            case "ASSIGNMENT_LEASE_EXPIRED" -> ASSIGNMENT_LEASE_EXPIRED;
            case "ASSIGNMENT_ID_MISMATCH" -> INVALID_FENCING_TOKEN;
            case "TASK_ALREADY_TERMINAL" -> TASK_ALREADY_TERMINAL;
            case "DISPATCH_ALREADY_TERMINAL" -> DISPATCH_ALREADY_TERMINAL;
            case "CONCURRENT_STATE_CONFLICT" -> CONCURRENT_STATE_CONFLICT;
            default -> BAD_REQUEST;
        };
    }
}
