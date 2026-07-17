package com.opensocket.aievent.gateway.netty.api;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Converts Netty admin API failures to HTTP 200 standard envelopes. */
@RestControllerAdvice(basePackages = "com.opensocket.aievent.gateway.netty")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayApiExceptionHandler {
    private final MeterRegistry meterRegistry;

    public GatewayApiExceptionHandler() {
        this.meterRegistry = null;
    }

    @Autowired
    public GatewayApiExceptionHandler(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
    }

    @ExceptionHandler(GatewayApiException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleGatewayApiException(GatewayApiException ex) {
        return error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(GatewayApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(GatewayApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(GatewayApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401) {
            return error(GatewayApiErrorCode.UNAUTHORIZED, reasonOrDefault(ex, GatewayApiErrorCode.UNAUTHORIZED.defaultMessage()));
        }
        if (status == 403) {
            return error(GatewayApiErrorCode.FORBIDDEN, reasonOrDefault(ex, GatewayApiErrorCode.FORBIDDEN.defaultMessage()));
        }
        if (status == 404) {
            return error(GatewayApiErrorCode.NOT_FOUND, reasonOrDefault(ex, GatewayApiErrorCode.NOT_FOUND.defaultMessage()));
        }
        if (status == 409) {
            return error(GatewayApiErrorCode.CONFLICT, reasonOrDefault(ex, GatewayApiErrorCode.CONFLICT.defaultMessage()));
        }
        if (status == 503) {
            return error(GatewayApiErrorCode.DEPENDENCY_UNAVAILABLE, reasonOrDefault(ex, GatewayApiErrorCode.DEPENDENCY_UNAVAILABLE.defaultMessage()));
        }
        return error(GatewayApiErrorCode.BAD_REQUEST, reasonOrDefault(ex, GatewayApiErrorCode.BAD_REQUEST.defaultMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(GatewayApiErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return error(GatewayApiErrorCode.INVALID_STATE, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayApiResponse<Void>> handleUnexpected(Exception ex) {
        return error(GatewayApiErrorCode.INTERNAL_ERROR, GatewayApiErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<GatewayApiResponse<Void>> error(GatewayApiErrorCode code, String message) {
        return error(code.code(), message == null || message.isBlank() ? code.defaultMessage() : message);
    }

    private ResponseEntity<GatewayApiResponse<Void>> error(String code, String message) {
        recordEnvelopeError(code);
        return ResponseEntity.ok(GatewayApiResponse.error(code, message));
    }

    private void recordEnvelopeError(String code) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("opendispatch.api.envelope.total")
                .description("OpenDispatch API envelope responses by plane, outcome and envelope code. HTTP status may remain 200 for non-OK codes.")
                .tag("plane", "netty")
                .tag("outcome", "error")
                .tag("code", normalizeMetricTag(code))
                .tag("http_status", "200")
                .register(meterRegistry)
                .increment();
    }

    private String normalizeMetricTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private String reasonOrDefault(ResponseStatusException ex, String fallback) {
        return ex.getReason() == null || ex.getReason().isBlank() ? fallback : ex.getReason();
    }
}
