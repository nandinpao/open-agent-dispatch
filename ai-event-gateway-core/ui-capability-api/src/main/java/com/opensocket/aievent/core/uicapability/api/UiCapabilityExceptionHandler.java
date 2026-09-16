package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uicapability.core.UiCapabilityProjectionException;
import com.opensocket.aievent.core.uicapability.core.UiPageBootstrapException;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {UiCapabilityController.class, UiPageBootstrapController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "projection-api-enabled"}, havingValue = "true")
@ConditionalOnBean(UiCapabilityApiContextPort.class)
public final class UiCapabilityExceptionHandler {
    private final UiCapabilityApiContextPort contexts; private final Clock clock;
    public UiCapabilityExceptionHandler(UiCapabilityApiContextPort contexts, Clock clock) {
        this.contexts = contexts; this.clock = clock;
    }
    @ExceptionHandler(UiPageBootstrapException.class)
    ResponseEntity<UiCapabilityApiError> bootstrap(UiPageBootstrapException error) {
        HttpStatus status = error.code() == UiPageBootstrapException.Code.UI_PAGE_CONTEXT_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new UiCapabilityApiError(error.code().name(),
                "The protected page is not available.", false, correlationId(), clock.instant()));
    }
    @ExceptionHandler(UiCapabilityProjectionException.class)
    ResponseEntity<UiCapabilityApiError> projection(UiCapabilityProjectionException error) {
        HttpStatus status = switch (error.code()) {
            case UI_CAPABILITY_PRINCIPAL_EPOCH_STALE -> HttpStatus.CONFLICT;
            case UI_CAPABILITY_PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new UiCapabilityApiError(error.code().name(),
                safeMessage(error.code()), false, correlationId(), clock.instant()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<UiCapabilityApiError> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new UiCapabilityApiError("UI_CAPABILITY_CONTEXT_INVALID",
                "The capability request is invalid.", false, correlationId(), clock.instant()));
    }
    private String correlationId() {
        try { return contexts.current().correlationId(); } catch (RuntimeException ignored) { return "unavailable"; }
    }
    private static String safeMessage(UiCapabilityProjectionException.Code code) {
        return switch (code) {
            case UI_CAPABILITY_CONTRACT_UNSUPPORTED -> "This UI capability contract version is not supported.";
            case UI_CAPABILITY_PRINCIPAL_EPOCH_STALE -> "Your access changed. Reload before continuing.";
            case UI_CAPABILITY_PAYLOAD_TOO_LARGE, UI_CAPABILITY_BATCH_LIMIT_EXCEEDED -> "The capability request is too large.";
            default -> "The capability context is invalid.";
        };
    }
}
