package com.opensocket.aievent.core.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;

import jakarta.servlet.http.HttpServletRequest;

/** Advanced/release-engineering API for ordered C3R3 Single Authority cutover waves. */
@RestController
@RequestMapping("/api/platform/runtime-configuration/cutover-waves")
public class RuntimeConfigurationCutoverWaveController {
    private final RuntimeConfigurationCutoverWaveService service;
    private final RuntimeConfigurationCutoverWaveCertificationService certification;
    private final RuntimeConfigurationCutoverWaveSafetyAttestationService safety;

    public RuntimeConfigurationCutoverWaveController(RuntimeConfigurationCutoverWaveService service,
            RuntimeConfigurationCutoverWaveCertificationService certification,
            RuntimeConfigurationCutoverWaveSafetyAttestationService safety) {
        this.service = service; this.certification = certification; this.safety = safety;
    }

    @GetMapping
    public List<RuntimeConfigurationCutoverWaveService.WaveStatus> list() { return invoke(service::listStatus); }

    @GetMapping("/{waveId}")
    public RuntimeConfigurationCutoverWaveService.WaveStatus status(@PathVariable String waveId) {
        return invoke(() -> service.status(waveId));
    }

    @PostMapping("/{waveId}/prepare")
    public RuntimeConfigurationCutoverWaveService.WaveStatus prepare(@PathVariable String waveId,
            @RequestBody ReasonRequest body, Authentication authentication, HttpServletRequest request) {
        return invoke(() -> service.prepare(waveId, actor(authentication), body.reason(), correlationId(request)));
    }

    @PostMapping("/{waveId}/finalize")
    public RuntimeConfigurationCutoverWaveService.WaveStatus finalizeWave(@PathVariable String waveId,
            @RequestBody ReasonRequest body, Authentication authentication, HttpServletRequest request) {
        return invoke(() -> service.finalizeWave(waveId, actor(authentication), body.reason(), correlationId(request)));
    }


    @GetMapping("/{waveId}/safety-attestation")
    public RuntimeConfigurationCutoverWaveSafetyAttestationService.AttestationStatus safetyAttestation(@PathVariable String waveId) {
        return invoke(() -> safety.status(waveId));
    }

    @PostMapping("/{waveId}/safety-attestation")
    public RuntimeConfigurationCutoverWaveSafetyAttestationService.AttestationStatus assessSafety(@PathVariable String waveId,
            @RequestBody ReasonRequest body, Authentication authentication, HttpServletRequest request) {
        return invoke(() -> safety.assess(waveId, actor(authentication), body.reason(), correlationId(request)));
    }

    @GetMapping("/{waveId}/certification")
    public RuntimeConfigurationCutoverWaveCertificationService.CertificationStatus certification(@PathVariable String waveId) {
        return invoke(() -> certification.status(waveId));
    }

    @PostMapping("/{waveId}/certify")
    public com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertification certify(
            @PathVariable String waveId,@RequestBody ReasonRequest body,Authentication authentication,HttpServletRequest request) {
        return invoke(() -> certification.certify(waveId,actor(authentication),body.reason(),correlationId(request)));
    }

    @PostMapping("/{waveId}/cancel")
    public RuntimeConfigurationCutoverWaveService.WaveStatus cancel(@PathVariable String waveId,
            @RequestBody ReasonRequest body, Authentication authentication, HttpServletRequest request) {
        return invoke(() -> service.cancel(waveId, actor(authentication), body.reason(), correlationId(request)));
    }

    private <T> T invoke(Action<T> action) {
        try { return action.run(); }
        catch (IllegalArgumentException | IllegalStateException ex) {
            throw new StandardApiException(StandardApiErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
        }
    }

    private static String actor(Authentication a) {
        return a == null || a.getName() == null || a.getName().isBlank() ? "unknown-platform-operator" : a.getName();
    }

    private static String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    public record ReasonRequest(String reason) {}
    @FunctionalInterface private interface Action<T> { T run(); }
}
