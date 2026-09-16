package com.opensocket.aievent.core.api;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanException;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotException;

@RestControllerAdvice(assignableTypes = {EnforcementActivationController.class, Wave0ReadPilotController.class})
@ConditionalOnProperty(
        prefix = "aeg.enforcement-activation",
        name = "control-plane-enabled",
        havingValue = "true")
public class EnforcementActivationExceptionHandler {
    @ExceptionHandler(CutoverPlanException.class)
    ResponseEntity<Map<String, String>> handle(CutoverPlanException error) {
        HttpStatus status = status(error.code());
        return ResponseEntity.status(status).body(Map.of(
                "error_code", error.code(),
                "message", error.getMessage()));
    }

    @ExceptionHandler(Wave0ReadPilotException.class)
    ResponseEntity<Map<String, String>> wave0(Wave0ReadPilotException error) {
        HttpStatus status = status(error.code());
        return ResponseEntity.status(status).body(Map.of(
                "error_code", error.code(),
                "message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "error_code", "ENFORCEMENT_ACTIVATION_REQUEST_INVALID",
                "message", error.getMessage()));
    }

    private static HttpStatus status(String code) {
        if (code.contains("NOT_FOUND")) return HttpStatus.NOT_FOUND;
        if (code.contains("SOD_VIOLATION")) return HttpStatus.FORBIDDEN;
        if (code.contains("VERSION_CONFLICT")
                || code.contains("IDEMPOTENCY_CONFLICT")
                || code.contains("STATE_INVALID")
                || code.contains("ALREADY_BOUND")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
