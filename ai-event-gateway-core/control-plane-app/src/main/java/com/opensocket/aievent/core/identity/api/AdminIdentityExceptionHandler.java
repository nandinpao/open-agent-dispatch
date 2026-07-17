package com.opensocket.aievent.core.identity.api;

import java.time.OffsetDateTime;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.opensocket.aievent.core.identity.service.AdminAuthenticationDisabledException;
import com.opensocket.aievent.core.identity.service.AdminSessionAccessDeniedException;
import com.opensocket.aievent.core.identity.service.AdminSessionNotFoundException;

@RestControllerAdvice(assignableTypes = AdminAuthenticationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminIdentityExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AdminAuthError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid authentication request.");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AdminAuthError> badCredentials() {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
    }

    @ExceptionHandler(AdminAuthenticationDisabledException.class)
    public ResponseEntity<AdminAuthError> disabled(AdminAuthenticationDisabledException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_AUTH_DISABLED", exception.getMessage());
    }

    @ExceptionHandler(AdminSessionAccessDeniedException.class)
    public ResponseEntity<AdminAuthError> sessionDenied(AdminSessionAccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(AdminSessionNotFoundException.class)
    public ResponseEntity<AdminAuthError> sessionMissing(AdminSessionNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AdminAuthError> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<AdminAuthError> invalidState(IllegalStateException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    private ResponseEntity<AdminAuthError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new AdminAuthError(code, message, OffsetDateTime.now()));
    }

    public record AdminAuthError(String code, String message, OffsetDateTime occurredAt) {}
}
