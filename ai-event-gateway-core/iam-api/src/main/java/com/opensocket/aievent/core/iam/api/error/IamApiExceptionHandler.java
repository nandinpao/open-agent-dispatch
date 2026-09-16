package com.opensocket.aievent.core.iam.api.error;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.security.IamAuthorizationException;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationDomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.opensocket.aievent.core.iam.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public final class IamApiExceptionHandler {
    private final IamApiRequestContextFactory contexts;
    private final Clock clock;

    public IamApiExceptionHandler(IamApiRequestContextFactory contexts, Clock clock) {
        this.contexts = contexts;
        this.clock = clock;
    }

    @ExceptionHandler(IamApiException.class)
    ResponseEntity<IamApiErrorResponse> api(IamApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(body(
                exception.errorCode(),
                exception.getMessage(),
                exception.requiredPermission(),
                List.of(),
                request));
    }

    @ExceptionHandler(OrganizationDomainException.class)
    ResponseEntity<IamApiErrorResponse> organizationDomain(
            OrganizationDomainException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(status(exception.reasonCode())).body(body(
                exception.reasonCode(),
                exception.getMessage(),
                "",
                List.of(),
                request));
    }

    @ExceptionHandler(IamAuthorizationException.class)
    ResponseEntity<IamApiErrorResponse> denied(
            IamAuthorizationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(
                exception.decision().reasonCode(),
                "Permission denied",
                exception.requiredPermission(),
                List.of(),
                request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<IamApiErrorResponse> validation(Exception exception, HttpServletRequest request) {
        var errors = exception instanceof MethodArgumentNotValidException methodArgument
                ? methodArgument.getBindingResult().getFieldErrors()
                : ((BindException) exception).getBindingResult().getFieldErrors();
        var fields = errors.stream()
                .map(error -> new IamFieldError(
                        error.getField(),
                        "IAM_FIELD_INVALID",
                        error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(body(
                "IAM_VALIDATION_FAILED",
                "Request validation failed",
                "",
                fields,
                request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<IamApiErrorResponse> constraint(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        var fields = exception.getConstraintViolations().stream()
                .map(violation -> new IamFieldError(
                        violation.getPropertyPath().toString(),
                        "IAM_FIELD_INVALID",
                        violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(body(
                "IAM_VALIDATION_FAILED",
                "Request validation failed",
                "",
                fields,
                request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<IamApiErrorResponse> domain(RuntimeException exception, HttpServletRequest request) {
        String errorCode = code(exception);
        return ResponseEntity.status(status(errorCode)).body(body(
                errorCode,
                exception.getMessage(),
                "",
                List.of(),
                request));
    }

    private IamApiErrorResponse body(
            String errorCode,
            String message,
            String permission,
            List<IamFieldError> fields,
            HttpServletRequest request) {
        String correlationId = safeCorrelationId(request);
        String activeTenantId = "";
        try {
            var context = contexts.from(request);
            correlationId = context.correlationId();
            activeTenantId = context.authentication()
                    .filter(authentication -> authentication.activeTenant().scope()
                            == com.opensocket.aievent.core.iam.security.contract.TenantRef.Scope.TENANT)
                    .map(authentication -> authentication.activeTenant().tenantId())
                    .orElse("");
        } catch (RuntimeException ignored) {
            // Error rendering must not fail because the original request carried malformed headers.
        }
        return new IamApiErrorResponse(
                errorCode,
                message == null ? "Request failed" : message,
                correlationId,
                fields,
                permission,
                activeTenantId,
                clock.instant());
    }

    private String safeCorrelationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        if (value == null || value.isBlank()) {
            value = request.getHeader("X-Request-Id");
        }
        if (value == null || value.isBlank() || value.length() > 128) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    private String code(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.matches("[A-Z][A-Z0-9_]{2,120}")) {
            return message;
        }
        return "IAM_REQUEST_REJECTED";
    }

    private HttpStatus status(String errorCode) {
        if (errorCode.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode.equals("AUTHENTICATION_REQUIRED")
                || errorCode.startsWith("AUTH_INVALID_")
                || errorCode.startsWith("AUTH_SESSION_")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (errorCode.startsWith("AUTH_PERMISSION_")
                || errorCode.startsWith("AUTH_SCOPE_")
                || errorCode.startsWith("TENANT_CONTEXT_")) {
            return HttpStatus.FORBIDDEN;
        }
        if (errorCode.equals("PERMISSION_CATALOG_ACTIVE_PROJECTION_FAILED")
                || errorCode.equals("PERMISSION_CATALOG_CHANGE_EVENT_FAILED")
                || errorCode.equals("PERMISSION_CATALOG_PUBLICATION_EVENT_FAILED")) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (errorCode.contains("VERSION_CONFLICT")
                || errorCode.contains("LAST_TENANT_ADMIN")
                || errorCode.endsWith("_CONFLICT")
                || errorCode.equals("PERMISSION_CATALOG_ACTIVE_DRAFT_EXISTS")
                || errorCode.equals("PERMISSION_CATALOG_REVISION_NOT_DRAFT")
                || errorCode.equals("PERMISSION_CATALOG_EXISTING_PERMISSION_MUST_BE_RETIRED")
                || errorCode.equals("PERMISSION_CATALOG_LIFECYCLE_TRANSITION_INVALID")
                || errorCode.equals("PERMISSION_CATALOG_VALIDATION_FAILED")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
