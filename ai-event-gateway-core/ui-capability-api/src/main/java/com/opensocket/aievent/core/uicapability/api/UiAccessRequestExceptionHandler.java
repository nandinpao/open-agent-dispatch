package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestException;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UiAccessRequestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "access-request-enabled"}, havingValue = "true")
@ConditionalOnBean(UiCapabilityApiContextPort.class)
public final class UiAccessRequestExceptionHandler {
    private final UiCapabilityApiContextPort contexts; private final Clock clock;
    public UiAccessRequestExceptionHandler(UiCapabilityApiContextPort contexts, Clock clock) {
        this.contexts = contexts; this.clock = clock;
    }
    @ExceptionHandler(GovernedAccessRequestException.class)
    ResponseEntity<UiCapabilityApiError> governed(GovernedAccessRequestException error) {
        return ResponseEntity.status(status(error.code())).body(new UiCapabilityApiError(error.code().name(),
                safeMessage(error.code()), retryable(error.code()), correlationId(), clock.instant()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<UiCapabilityApiError> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new UiCapabilityApiError("ACCESS_REQUEST_INVALID",
                "The access request is invalid.", false, correlationId(), clock.instant()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<UiCapabilityApiError> state(IllegalStateException error) {
        String code = "RESOURCE_VERSION_CONFLICT".equals(error.getMessage())
                ? "ACCESS_REQUEST_VERSION_CONFLICT" : "ACCESS_REQUEST_INVALID_STATE";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new UiCapabilityApiError(code,
                "The access request changed while you were working.", true, correlationId(), clock.instant()));
    }
    private String correlationId() {
        try { return contexts.current().correlationId(); } catch (RuntimeException ignored) { return "unavailable"; }
    }
    private static HttpStatus status(GovernedAccessRequestException.Code code) {
        return switch (code) {
            case ACCESS_REQUEST_RESOURCE_NOT_FOUND, ACCESS_REQUEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCESS_REQUEST_RESOURCE_VERSION_STALE -> HttpStatus.PRECONDITION_FAILED;
            case ACCESS_REQUEST_ACTION_NOT_REQUESTABLE, ACCESS_REQUEST_VISIBILITY_NOT_ALLOWED,
                    ACCESS_REQUEST_APPROVER_NOT_AUTHORIZED -> HttpStatus.FORBIDDEN;
            case ACCESS_REQUEST_DURATION_NOT_ALLOWED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
    }
    private static boolean retryable(GovernedAccessRequestException.Code code) {
        return code == GovernedAccessRequestException.Code.ACCESS_REQUEST_RESOURCE_VERSION_STALE
                || code == GovernedAccessRequestException.Code.ACCESS_REQUEST_VERSION_CONFLICT;
    }
    private static String safeMessage(GovernedAccessRequestException.Code code) {
        return switch (code) {
            case ACCESS_REQUEST_RESOURCE_NOT_FOUND, ACCESS_REQUEST_NOT_FOUND -> "The resource or access request is unavailable.";
            case ACCESS_REQUEST_RESOURCE_VERSION_STALE -> "The resource changed. Review the latest information before continuing.";
            case ACCESS_REQUEST_ACTION_NOT_REQUESTABLE -> "This action does not support a governed access request.";
            case ACCESS_REQUEST_VISIBILITY_NOT_ALLOWED -> "The requested visibility is not allowed for this action.";
            case ACCESS_REQUEST_DURATION_NOT_ALLOWED -> "The requested access duration is not allowed.";
            case ACCESS_REQUEST_SEPARATION_OF_DUTIES -> "The requester cannot approve or reject the same access request.";
            case ACCESS_REQUEST_APPROVER_NOT_AUTHORIZED -> "You are not authorized to approve this request for the resource.";
            case ACCESS_REQUEST_GRANT_DRIFT -> "The access request no longer matches its canonical Scope Grant.";
            default -> "The access request changed while you were working.";
        };
    }
}
