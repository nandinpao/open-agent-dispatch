package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMetricSummary;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotOverview;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;

public final class Wave0ReadPilotGateService {
    private final Wave0ReadPilotRepository repository;
    private final Clock clock;
    private final boolean executionPropertyEnabled;
    private final Set<Wave0ReadPilotEntryPoint> additionalExecutableEntryPoints;

    public Wave0ReadPilotGateService(
            Wave0ReadPilotRepository repository,
            Clock clock,
            boolean executionPropertyEnabled) {
        this(repository, clock, executionPropertyEnabled, Set.of());
    }

    public Wave0ReadPilotGateService(
            Wave0ReadPilotRepository repository,
            Clock clock,
            boolean executionPropertyEnabled,
            Set<Wave0ReadPilotEntryPoint> additionalExecutableEntryPoints) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executionPropertyEnabled = executionPropertyEnabled;
        this.additionalExecutableEntryPoints = additionalExecutableEntryPoints == null
                ? Set.of() : Set.copyOf(additionalExecutableEntryPoints);
    }

    public List<Wave0ReadPilotOverview> overview() {
        Map<Wave0ReadPilotEntryPoint, Wave0ReadPilotGateStatus> gates = new EnumMap<>(Wave0ReadPilotEntryPoint.class);
        repository.gates().forEach(gate -> gates.put(gate.entryPoint(), gate));
        Instant now = clock.instant();
        return repository.policies().stream().map(policy -> {
            Wave0ReadPilotGateStatus gate = gates.get(policy.entryPoint());
            Wave0ReadPilotMetricSummary metrics = repository.metrics(
                    policy.entryPoint(), now.minus(policy.observationWindow()), now);
            boolean routeEligible = isExecutable(policy.entryPoint());
            String blocker = blockingReason(policy, gate, routeEligible);
            boolean executable = executionPropertyEnabled && policy.enabled() && gate.permitsPilotExecution() && routeEligible;
            return new Wave0ReadPilotOverview(policy, gate, metrics, executionPropertyEnabled, routeEligible, blocker, executable);
        }).toList();
    }

    public Wave0ReadPilotGateStatus pause(
            Wave0ReadPilotEntryPoint entryPoint,
            long expectedVersion,
            String reason,
            String actorId,
            String correlationId) {
        requireReason(reason);
        return repository.transition(entryPoint, expectedVersion, Wave0ReadPilotGateState.PAUSED,
                "MANUAL_PAUSE:" + reason.trim(), actorId, correlationId, clock.instant());
    }

    public Wave0ReadPilotGateStatus resume(
            Wave0ReadPilotEntryPoint entryPoint,
            long expectedVersion,
            String reason,
            String actorId,
            String correlationId) {
        requireReason(reason);
        if (!executionPropertyEnabled) {
            throw new Wave0ReadPilotException("WAVE0_EXECUTION_PROPERTY_DISABLED", "Wave 0 execution property is disabled until Phase 6C-0 full certification passes");
        }
        if (!isExecutable(entryPoint)) {
            throw new Wave0ReadPilotException("WAVE0_ENTRY_POINT_NOT_CERTIFIED", entryPoint + " is not executable in Phase 6C-1");
        }
        return repository.transition(entryPoint, expectedVersion, Wave0ReadPilotGateState.OBSERVING,
                "MANUAL_RESUME:" + reason.trim(), actorId, correlationId, clock.instant());
    }

    public Wave0ReadPilotGateStatus evaluate(
            Wave0ReadPilotEntryPoint entryPoint,
            String actorId,
            String correlationId) {
        Wave0ReadPilotPolicy policy = repository.policy(entryPoint);
        Wave0ReadPilotGateStatus current = repository.gate(entryPoint);
        if (!executionPropertyEnabled || !policy.enabled() || !isExecutable(entryPoint)) return current;
        if (current.state() == Wave0ReadPilotGateState.DISABLED || current.state() == Wave0ReadPilotGateState.PAUSED) return current;
        Instant now = clock.instant();
        Wave0ReadPilotMetricSummary metrics = repository.metrics(entryPoint, now.minus(policy.observationWindow()), now);
        Wave0ReadPilotGateState next;
        String reason;
        if (metrics.sampleCount() < policy.minimumSamples()) {
            next = Wave0ReadPilotGateState.OBSERVING;
            reason = "MINIMUM_SAMPLE_WINDOW_NOT_MET";
        } else if (metrics.mismatchBasisPoints() > policy.maximumMismatchBasisPoints()) {
            next = policy.autoPause() ? Wave0ReadPilotGateState.PAUSED : Wave0ReadPilotGateState.BLOCKED;
            reason = "MISMATCH_ERROR_BUDGET_BURN";
        } else if (metrics.targetErrorBasisPoints() > policy.maximumTargetErrorBasisPoints()) {
            next = policy.autoPause() ? Wave0ReadPilotGateState.PAUSED : Wave0ReadPilotGateState.BLOCKED;
            reason = "TARGET_ERROR_BUDGET_BURN";
        } else if (metrics.targetP95Millis() > policy.maximumTargetP95Millis()) {
            next = policy.autoPause() ? Wave0ReadPilotGateState.PAUSED : Wave0ReadPilotGateState.BLOCKED;
            reason = "TARGET_LATENCY_ERROR_BUDGET_BURN";
        } else {
            next = Wave0ReadPilotGateState.OPEN;
            reason = "ERROR_BUDGET_HEALTHY";
        }
        if (next == current.state() && reason.equals(current.reasonCode())) return current;
        return repository.transition(entryPoint, current.version(), next, reason, actorId, correlationId, now);
    }


    private boolean isExecutable(Wave0ReadPilotEntryPoint entryPoint) {
        return entryPoint.phase6c1Executable() || additionalExecutableEntryPoints.contains(entryPoint);
    }

    private String blockingReason(
            Wave0ReadPilotPolicy policy,
            Wave0ReadPilotGateStatus gate,
            boolean routeEligible) {
        if (!executionPropertyEnabled) return "WAVE0_EXECUTION_PROPERTY_DISABLED";
        if (!policy.enabled()) return "ENTRY_POINT_POLICY_DISABLED";
        if (!routeEligible) return "ENTRY_POINT_DEFERRED_TO_LATER_WAVE";
        if (!gate.permitsPilotExecution()) return gate.reasonCode();
        return "";
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 12) throw new Wave0ReadPilotException("AUDIT_REASON_REQUIRED", "Audit reason must contain at least 12 characters");
    }
}
