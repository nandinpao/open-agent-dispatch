package com.opensocket.aievent.core.configuration;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;

import jakarta.servlet.http.HttpServletRequest;

/** Explicit operator cutover workflow. Approval/publish remains on the existing governed revision API. */
@RestController
@RequestMapping("/api/platform/runtime-configuration/migration-cutovers/adapter-executor-circuit-breaker")
public class RuntimeConfigurationMigrationCutoverController {
    private final RuntimeConfigurationMigrationCutoverService service;

    public RuntimeConfigurationMigrationCutoverController(RuntimeConfigurationMigrationCutoverService service) {
        this.service = service;
    }

    @GetMapping
    public RuntimeConfigurationMigrationCutoverService.CutoverStatus status() {
        return service.adapterExecutorCircuitBreakerStatus();
    }

    @PostMapping("/prepare")
    public RuntimeConfigurationMigrationCutoverService.CutoverPreparation prepare(@RequestBody ReasonRequest body,
            Authentication authentication, HttpServletRequest request) {
        try {
            return service.prepareAdapterExecutorCircuitBreaker(actor(authentication), body.reason(), correlationId(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw validation(ex);
        }
    }

    @PostMapping("/finalize")
    public RuntimeConfigurationMigrationCutoverService.CutoverFinalization finalizeCutover(@RequestBody ReasonRequest body,
            Authentication authentication, HttpServletRequest request) {
        try {
            return service.finalizeAdapterExecutorCircuitBreaker(actor(authentication), body.reason(), correlationId(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw validation(ex);
        }
    }

    private static StandardApiException validation(RuntimeException ex) {
        return new StandardApiException(StandardApiErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
    }
    private static String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "unknown-platform-operator" : authentication.getName();
    }
    private static String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        if (value == null || value.isBlank()) value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    public record ReasonRequest(String reason) {}
}
