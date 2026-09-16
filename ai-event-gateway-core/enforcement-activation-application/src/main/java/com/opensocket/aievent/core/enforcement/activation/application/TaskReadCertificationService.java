package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationEvidence;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMetricSummary;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;

public final class TaskReadCertificationService {
    private final TaskReadCertificationRepository certifications;
    private final Wave0ReadPilotRepository pilot;
    private final Clock clock;

    public TaskReadCertificationService(TaskReadCertificationRepository certifications, Wave0ReadPilotRepository pilot, Clock clock) {
        this.certifications = Objects.requireNonNull(certifications, "certifications");
        this.pilot = Objects.requireNonNull(pilot, "pilot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TaskReadCertificationEvidence certify(Command command) {
        Objects.requireNonNull(command, "command");
        if (command.routeMode() != AuthorityMode.SHADOW && command.routeMode() != AuthorityMode.TARGET_CANARY) {
            throw new Wave0ReadPilotException("TASK_READ_CERTIFICATION_MODE_INVALID", "Only SHADOW or TARGET_CANARY can be certified");
        }
        Wave0ReadPilotPolicy policy = pilot.policy(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH);
        var gate = pilot.gate(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH);
        if (gate.state() == Wave0ReadPilotGateState.DISABLED || gate.state() == Wave0ReadPilotGateState.BLOCKED) {
            throw new Wave0ReadPilotException("TASK_READ_CERTIFICATION_GATE_INVALID", "Task read Gate must have executed before certification");
        }
        Instant now = clock.instant();
        Wave0ReadPilotMetricSummary metrics = pilot.metrics(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH, now.minus(policy.observationWindow()), now);
        boolean metricsHealthy = metrics.sampleCount() >= policy.minimumSamples()
                && metrics.mismatchBasisPoints() <= policy.maximumMismatchBasisPoints()
                && metrics.targetErrorBasisPoints() <= policy.maximumTargetErrorBasisPoints()
                && metrics.targetP95Millis() <= policy.maximumTargetP95Millis();
        boolean checksPass = command.statuses().stream().allMatch(status -> status == TaskReadCertificationStatus.PASS);
        TaskReadCertificationStatus overall = metricsHealthy && checksPass
                ? TaskReadCertificationStatus.PASS : TaskReadCertificationStatus.BLOCKED;
        TaskReadCertificationEvidence evidence = new TaskReadCertificationEvidence(
                UUID.randomUUID(), command.tenantId(), command.authorityRevision(), command.routeMode(), metrics.sampleCount(),
                command.deterministicOrderStatus(), command.cursorPaginationStatus(), command.nPlusOneStatus(),
                command.forceRlsStatus(), command.sensitiveFieldMaskingStatus(), command.fallbackPauseStatus(),
                command.loadTestStatus(), overall, command.evidenceJson(), command.idempotencyKey(),
                requestHash(command), command.actorId(), command.correlationId(), now);
        return certifications.save(evidence);
    }

    public List<TaskReadCertificationEvidence> recent(String tenantId, int limit) {
        return certifications.recent(tenantId, Math.max(1, Math.min(limit, 100)));
    }

    private static String requestHash(Command command) {
        String canonical = String.join("|",
                command.tenantId().trim(), Long.toString(command.authorityRevision()), command.routeMode().name(),
                command.deterministicOrderStatus().name(), command.cursorPaginationStatus().name(),
                command.nPlusOneStatus().name(), command.forceRlsStatus().name(),
                command.sensitiveFieldMaskingStatus().name(), command.fallbackPauseStatus().name(),
                command.loadTestStatus().name(), command.evidenceJson() == null ? "{}" : command.evidenceJson().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Command(
            String tenantId, long authorityRevision, AuthorityMode routeMode,
            TaskReadCertificationStatus deterministicOrderStatus,
            TaskReadCertificationStatus cursorPaginationStatus,
            TaskReadCertificationStatus nPlusOneStatus,
            TaskReadCertificationStatus forceRlsStatus,
            TaskReadCertificationStatus sensitiveFieldMaskingStatus,
            TaskReadCertificationStatus fallbackPauseStatus,
            TaskReadCertificationStatus loadTestStatus,
            String evidenceJson, String actorId, String correlationId, String idempotencyKey) {
        public Command {
            if (tenantId == null || tenantId.isBlank() || authorityRevision < 1) throw new IllegalArgumentException("tenantId and authorityRevision are required");
            Objects.requireNonNull(routeMode, "routeMode");
            Objects.requireNonNull(deterministicOrderStatus, "deterministicOrderStatus");
            Objects.requireNonNull(cursorPaginationStatus, "cursorPaginationStatus");
            Objects.requireNonNull(nPlusOneStatus, "nPlusOneStatus");
            Objects.requireNonNull(forceRlsStatus, "forceRlsStatus");
            Objects.requireNonNull(sensitiveFieldMaskingStatus, "sensitiveFieldMaskingStatus");
            Objects.requireNonNull(fallbackPauseStatus, "fallbackPauseStatus");
            Objects.requireNonNull(loadTestStatus, "loadTestStatus");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        }
        List<TaskReadCertificationStatus> statuses() {
            return List.of(deterministicOrderStatus, cursorPaginationStatus, nPlusOneStatus, forceRlsStatus,
                    sensitiveFieldMaskingStatus, fallbackPauseStatus, loadTestStatus);
        }
    }
}
