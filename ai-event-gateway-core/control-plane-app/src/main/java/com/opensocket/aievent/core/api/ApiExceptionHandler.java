package com.opensocket.aievent.core.api;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public ApiExceptionHandler() {
        this.meterRegistry = null;
    }

    @Autowired
    public ApiExceptionHandler(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
    }

    @ExceptionHandler(StandardApiException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleStandardApi(StandardApiException ex) {
        return error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, validationMessage(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleBind(BindException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, validationMessage(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        return error(mapStatus(ex.getStatusCode()), ex.getReason() == null || ex.getReason().isBlank() ? ex.getMessage() : ex.getReason());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logApiFailure("api_method_not_allowed", request, ex);
        String supported = ex.getSupportedHttpMethods() == null || ex.getSupportedHttpMethods().isEmpty()
                ? ""
                : " Supported methods: " + ex.getSupportedHttpMethods();
        return error(StandardApiErrorCode.METHOD_NOT_ALLOWED, ex.getMessage() + supported);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        String name = ex.getParameterName();
        if ("tenantId".equalsIgnoreCase(name)) {
            return error(StandardApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantId is required");
        }
        return error(StandardApiErrorCode.BAD_REQUEST, name + " is required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return error(StandardApiErrorCode.INVALID_STATE, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        logApiFailure("api_data_integrity_violation", request, ex);
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("uq_agent_runtime_bindings_active_agent")
                || message.contains("agent_runtime_bindings")) {
            return error(StandardApiErrorCode.AGENT_RUNTIME_BINDING_CONFLICT,
                    "An active runtime binding already exists for this Agent. Re-run setup should update the existing binding instead of creating a duplicate.");
        }
        return error(StandardApiErrorCode.CONFLICT, "A database uniqueness constraint rejected the request.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        logApiFailure("api_database_failure", request, ex);
        return error(StandardApiErrorCode.INTERNAL_ERROR, StandardApiErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        logApiFailure("api_unhandled_exception", request, ex);
        return error(StandardApiErrorCode.INTERNAL_ERROR, StandardApiErrorCode.INTERNAL_ERROR.defaultMessage());
    }


    private void logApiFailure(String event, HttpServletRequest request, Exception ex) {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElse(null);
        Throwable root = rootCause(ex);
        String method = request == null ? "unknown" : request.getMethod();
        String path = request == null ? "unknown" : request.getRequestURI();
        String query = request == null ? "" : nullToEmpty(request.getQueryString());
        log.error("{} method={} path={} query={} requestId={} correlationId={} tenantId={} operatorId={} clientIp={} exception={} rootException={} rootMessage={}",
                event,
                safe(method),
                safe(path),
                safe(query),
                context == null ? "" : safe(context.requestId()),
                context == null ? "" : safe(context.correlationId()),
                context == null ? "" : safe(context.tenantId()),
                context == null ? "" : safe(context.operatorId()),
                context == null ? "" : safe(context.clientAddress()),
                ex == null ? "" : ex.getClass().getName(),
                root == null ? "" : root.getClass().getName(),
                root == null ? "" : safe(root.getMessage()),
                ex);
    }

    private Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        Throwable previous = null;
        while (current != null && current != previous && current.getCause() != null) {
            previous = current;
            current = current.getCause();
        }
        return current == null ? ex : current;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512) + "...";
    }

    private ResponseEntity<StandardApiResponse<Void>> error(StandardApiErrorCode code, String message) {
        recordEnvelopeError(code.code(), statusFor(code.code()));
        return ResponseEntity.status(statusFor(code.code())).body(StandardApiResponse.error(code, message));
    }

    private ResponseEntity<StandardApiResponse<Void>> error(String code, String message) {
        HttpStatus status = statusFor(code);
        recordEnvelopeError(code, status);
        return ResponseEntity.status(status).body(StandardApiResponse.error(code, message));
    }

    private void recordEnvelopeError(String code, HttpStatus status) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("opendispatch.api.envelope.total")
                .description("OpenDispatch API envelope responses by plane, outcome, code and HTTP status.")
                .tag("plane", "core")
                .tag("outcome", "error")
                .tag("code", normalizeMetricTag(code))
                .tag("http_status", String.valueOf(status == null ? 500 : status.value()))
                .register(meterRegistry)
                .increment();
    }

    private String normalizeMetricTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private HttpStatus statusFor(String code) {
        if (code == null || code.isBlank()) return HttpStatus.INTERNAL_SERVER_ERROR;
        String normalized = code.trim().toUpperCase();
        return switch (normalized) {
            case "BAD_REQUEST", "VALIDATION_ERROR", "TENANT_CONTEXT_REQUIRED", "FLOW_AGENT_PROFILE_NOT_FOUND" -> HttpStatus.BAD_REQUEST;
            case "METHOD_NOT_ALLOWED" -> HttpStatus.METHOD_NOT_ALLOWED;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND", "CORE_AGENT_NOT_FOUND", "CORE_TASK_NOT_FOUND", "CORE_INCIDENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT", "INVALID_STATE", "CORE_TASK_INVALID_TRANSITION", "CORE_CALLBACK_INVALID_TRANSITION",
                    "CORE_DISPATCH_DUPLICATE_REQUEST", "AGENT_RUNTIME_BINDING_CONFLICT" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> normalized.startsWith("CORE_") ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private StandardApiErrorCode mapStatus(HttpStatusCode status) {
        int value = status == null ? 500 : status.value();
        return switch (value) {
            case 400 -> StandardApiErrorCode.BAD_REQUEST;
            case 401 -> StandardApiErrorCode.UNAUTHORIZED;
            case 403 -> StandardApiErrorCode.FORBIDDEN;
            case 404 -> StandardApiErrorCode.NOT_FOUND;
            case 405 -> StandardApiErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> StandardApiErrorCode.CONFLICT;
            case 429 -> StandardApiErrorCode.RATE_LIMITED;
            case 504 -> StandardApiErrorCode.TIMEOUT;
            case 502, 503 -> StandardApiErrorCode.DEPENDENCY_UNAVAILABLE;
            default -> value >= 500 ? StandardApiErrorCode.INTERNAL_ERROR : StandardApiErrorCode.BAD_REQUEST;
        };
    }

    private String validationMessage(BindException ex) {
        if (ex.getFieldError() != null) {
            return ex.getFieldError().getField() + ": " + ex.getFieldError().getDefaultMessage();
        }
        if (ex.getGlobalError() != null) {
            return ex.getGlobalError().getDefaultMessage();
        }
        return StandardApiErrorCode.VALIDATION_ERROR.defaultMessage();
    }
}
