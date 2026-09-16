package com.opensocket.aievent.core.callback;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.opensocket.aievent.core.assignment.AssignmentFencingTokenPolicy;
import com.opensocket.aievent.core.assignment.AssignmentFencingValidation;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

/** Pure callback acceptance and fencing policy. */
final class TaskCallbackAcceptancePolicy {
    private TaskCallbackAcceptancePolicy() {
    }

    static String validate(
            TaskCallbackType type,
            TaskCallbackRequest request,
            DispatchRequest dispatch,
            TaskRecord task,
            TaskCallbackProperties properties,
            TaskAssignmentRepository assignmentRepository,
            AssignmentFencingTokenPolicy fencingTokenPolicy) {
        if (dispatch == null) return null;
        if (properties.isRequireDispatchToken()) {
            String expected = dispatch.getDispatchToken();
            if (expected == null || expected.isBlank()) return "DISPATCH_TOKEN_NOT_ISSUED";
            if (request.getDispatchToken() == null || request.getDispatchToken().isBlank()) return "DISPATCH_TOKEN_REQUIRED";
            if (!expected.equals(request.getDispatchToken())) return "INVALID_DISPATCH_TOKEN";
        }
        if (properties.isRejectOldAttemptCallbacks()) {
            if (request.getAttemptNo() == null) {
                if (properties.isRequireAttemptNo()) return "ATTEMPT_NO_REQUIRED";
            } else if (request.getAttemptNo() != dispatch.getAttemptCount()) {
                return request.getAttemptNo() < dispatch.getAttemptCount()
                        ? "OLD_ATTEMPT_CALLBACK"
                        : "FUTURE_ATTEMPT_CALLBACK";
            }
        }
        if (properties.isEnforceGatewayAndAgentIdentity()) {
            String identityError = requireMatching("agentId", request.getAgentId(), dispatch.getAgentId());
            if (identityError != null) return identityError;
            identityError = requireMatching("ownerGatewayNodeId", request.getOwnerGatewayNodeId(), dispatch.getOwnerGatewayNodeId());
            if (identityError != null) return identityError;
            identityError = requireMatching("agentSessionId", request.getAgentSessionId(), dispatch.getAgentSessionId());
            if (identityError != null) return identityError;
        }
        String fencingError = validateAssignmentFence(
                type, request, dispatch, properties, assignmentRepository, fencingTokenPolicy);
        if (fencingError != null) return fencingError;
        boolean cancellationCallback = isCancellationCallback(type, request)
                && task.getStatus() == TaskStatus.CANCEL_REQUESTED
                && dispatch.getStatus() == DispatchRequestStatus.CANCELLED;
        if (!properties.isAllowTerminalCallbackOverride() && !cancellationCallback) {
            if (isTerminal(task.getStatus())) return "TASK_ALREADY_TERMINAL";
            if (isTerminal(dispatch.getStatus())) return "DISPATCH_ALREADY_TERMINAL";
        }
        if (properties.isEnforceStateTransition() && !cancellationCallback
                && !isAllowedDispatchTransition(type, dispatch.getStatus())) {
            return "INVALID_DISPATCH_TRANSITION_" + dispatch.getStatus() + "_TO_" + type;
        }
        return null;
    }

    static boolean isCancellationCallback(TaskCallbackType type, TaskCallbackRequest request) {
        if (type != TaskCallbackType.RESULT && type != TaskCallbackType.ERROR) return false;
        return "CANCELLED".equalsIgnoreCase(request.getResultStatus())
                || "TASK_CANCELLED".equalsIgnoreCase(request.getErrorCode());
    }

    static boolean isTerminal(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    static boolean isTerminal(DispatchRequestStatus status) {
        return status == DispatchRequestStatus.COMPLETED
                || status == DispatchRequestStatus.FAILED
                || status == DispatchRequestStatus.TIMED_OUT
                || status == DispatchRequestStatus.CANCELLED
                || status == DispatchRequestStatus.REJECTED
                || status == DispatchRequestStatus.DEAD_LETTER;
    }

    static boolean isAllowedDispatchTransition(TaskCallbackType type, DispatchRequestStatus status) {
        if (status == null) return false;
        return switch (type) {
            case ACK -> status == DispatchRequestStatus.DISPATCHING
                    || status == DispatchRequestStatus.DELIVERY_UNKNOWN
                    || status == DispatchRequestStatus.DISPATCHED;
            case PROGRESS -> status == DispatchRequestStatus.DELIVERY_UNKNOWN
                    || status == DispatchRequestStatus.ACKED
                    || status == DispatchRequestStatus.RUNNING;
            case RESULT, ERROR -> status == DispatchRequestStatus.DISPATCHING
                    || status == DispatchRequestStatus.DELIVERY_UNKNOWN
                    || status == DispatchRequestStatus.DISPATCHED
                    || status == DispatchRequestStatus.ACKED
                    || status == DispatchRequestStatus.RUNNING;
        };
    }

    private static String validateAssignmentFence(
            TaskCallbackType type,
            TaskCallbackRequest request,
            DispatchRequest dispatch,
            TaskCallbackProperties properties,
            TaskAssignmentRepository assignmentRepository,
            AssignmentFencingTokenPolicy fencingTokenPolicy) {
        if (!properties.isEnforceAssignmentFencing() || assignmentRepository == null) return null;
        String assignmentId = firstNonBlank(request.getAssignmentId(), dispatch == null ? null : dispatch.getAssignmentId());
        if (assignmentId == null || assignmentId.isBlank()) {
            return properties.isRequireAssignmentIdForFencing() ? "ASSIGNMENT_ID_REQUIRED" : null;
        }
        TaskAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return properties.isRequireKnownAssignmentForFencing() ? "ASSIGNMENT_NOT_FOUND" : null;
        }
        if (isCancellationCallback(type, request)) {
            if (!assignmentId.equals(assignment.getAssignmentId())) return "ASSIGNMENT_ID_MISMATCH";
            if (request.getFencingToken() == null || request.getFencingToken().isBlank()) return "FENCING_TOKEN_REQUIRED";
            String originalDispatchFence = dispatch != null && dispatch.getCommand() != null
                    ? dispatch.getCommand().getFencingToken() : null;
            boolean currentFence = request.getFencingToken().equals(assignment.getFencingToken());
            boolean revokedDispatchFence = originalDispatchFence != null
                    && request.getFencingToken().equals(originalDispatchFence);
            return currentFence || revokedDispatchFence ? null : "INVALID_FENCING_TOKEN";
        }
        AssignmentFencingValidation validation = fencingTokenPolicy.validate(
                assignment, assignmentId, request.getFencingToken(), OffsetDateTime.now(ZoneOffset.UTC));
        return validation.accepted() ? null : validation.code();
    }

    private static String requireMatching(String field, String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return "EXPECTED_" + field + "_MISSING";
        }
        if (actual == null || actual.isBlank()) {
            return field + "_REQUIRED";
        }
        if (!expected.equals(actual)) {
            return field + "_MISMATCH";
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
