package com.opensocket.aievent.core.enforcement.activation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityPlane;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0CanonicalPayload;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMismatchCategory;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotObservation;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;
import com.opensocket.aievent.core.enforcement.activation.core.UnifiedEnforcementKernel;

public final class Wave0ReadPilotService {
    private final UnifiedEnforcementKernel kernel;
    private final Wave0ReadPilotRepository repository;
    private final Wave0ReadPilotGateService gates;
    private final Clock clock;
    private final boolean executionPropertyEnabled;
    private final Set<Wave0ReadPilotEntryPoint> additionalExecutableEntryPoints;

    public Wave0ReadPilotService(
            UnifiedEnforcementKernel kernel,
            Wave0ReadPilotRepository repository,
            Wave0ReadPilotGateService gates,
            Clock clock,
            boolean executionPropertyEnabled) {
        this(kernel, repository, gates, clock, executionPropertyEnabled, Set.of());
    }

    public Wave0ReadPilotService(
            UnifiedEnforcementKernel kernel,
            Wave0ReadPilotRepository repository,
            Wave0ReadPilotGateService gates,
            Clock clock,
            boolean executionPropertyEnabled,
            Set<Wave0ReadPilotEntryPoint> additionalExecutableEntryPoints) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gates = Objects.requireNonNull(gates, "gates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executionPropertyEnabled = executionPropertyEnabled;
        this.additionalExecutableEntryPoints = additionalExecutableEntryPoints == null
                ? Set.of() : Set.copyOf(additionalExecutableEntryPoints);
    }

    public boolean executionPropertyEnabled() { return executionPropertyEnabled; }

    public <T extends Wave0CanonicalPayload> Wave0ReadPilotResponse<T> execute(
            Wave0ReadPilotEntryPoint entryPoint,
            ActorContext actor,
            Supplier<T> legacyReader,
            Supplier<T> targetReader) {
        Objects.requireNonNull(entryPoint, "entryPoint");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(legacyReader, "legacyReader");
        Objects.requireNonNull(targetReader, "targetReader");

        Wave0ReadPilotPolicy policy = repository.policy(entryPoint);
        Wave0ReadPilotGateStatus gate = repository.gate(entryPoint);
        AuthorityDecision decision = kernel.decide(entryPoint.routingContext(
                actor.tenantId(), actor.cohortKey(), actor.correlationId()));
        if (decision.hardGuardDenied()) {
            throw new Wave0ReadPilotException("WAVE0_HARD_GUARD_DENIED", decision.reasonCode());
        }

        if (!executionPropertyEnabled || !policy.enabled() || !isExecutable(entryPoint)) {
            Invocation<T> legacy = invoke(legacyReader);
            return completeWithoutObservation(entryPoint, decision, gate.state(), legacy, "WAVE0_EXECUTION_DISABLED");
        }
        if (!gate.permitsPilotExecution()) {
            Invocation<T> legacy = invoke(legacyReader);
            return observeAndReturn(entryPoint, actor, decision, gate, legacy, Invocation.notRun(),
                    Wave0ReadPilotSource.LEGACY, false, false, Wave0ReadPilotMismatchCategory.NOT_COMPARED);
        }
        if (decision.mode() == AuthorityMode.TARGET_PRIMARY || decision.mode() == AuthorityMode.TARGET_ONLY) {
            Invocation<T> legacy = invoke(legacyReader);
            Wave0ReadPilotResponse<T> response = observeAndReturn(entryPoint, actor, decision, gate, legacy,
                    Invocation.notRun(), Wave0ReadPilotSource.LEGACY, false, false,
                    Wave0ReadPilotMismatchCategory.UNSUPPORTED_MODE);
            return response;
        }
        if (decision.mode() == AuthorityMode.SHADOW) {
            Invocation<T> legacy = invoke(legacyReader);
            Invocation<T> target = invoke(targetReader);
            Wave0ReadPilotResponse<T> response = observeAndReturn(entryPoint, actor, decision, gate, legacy, target,
                    Wave0ReadPilotSource.LEGACY, true, false, compare(legacy, target));
            return response;
        }
        if (decision.mode() == AuthorityMode.TARGET_CANARY && decision.plane() == AuthorityPlane.TARGET) {
            if (!gate.permitsTargetServing()) {
                Invocation<T> legacy = invoke(legacyReader);
                Invocation<T> target = invoke(targetReader);
                return observeAndReturn(entryPoint, actor, decision, gate, legacy, target,
                        Wave0ReadPilotSource.LEGACY, true, false, compare(legacy, target));
            }
            Invocation<T> target = invoke(targetReader);
            if (target.success()) {
                return observeAndReturn(entryPoint, actor, decision, gate,
                        Invocation.notRun(), target, Wave0ReadPilotSource.TARGET, false, false,
                        Wave0ReadPilotMismatchCategory.NOT_COMPARED);
            }
            Invocation<T> legacy = invoke(legacyReader);
            return observeAndReturn(entryPoint, actor, decision, gate, legacy, target,
                    Wave0ReadPilotSource.LEGACY, false, true, compare(legacy, target));
        }
        if (decision.mode() == AuthorityMode.TARGET_CANARY && policy.compareLegacyCanary()) {
            Invocation<T> legacy = invoke(legacyReader);
            Invocation<T> target = invoke(targetReader);
            Wave0ReadPilotResponse<T> response = observeAndReturn(entryPoint, actor, decision, gate, legacy, target,
                    Wave0ReadPilotSource.LEGACY, true, false, compare(legacy, target));
            return response;
        }

        Invocation<T> legacy = invoke(legacyReader);
        Wave0ReadPilotResponse<T> response = observeAndReturn(entryPoint, actor, decision, gate, legacy,
                Invocation.notRun(), Wave0ReadPilotSource.LEGACY, false, false,
                Wave0ReadPilotMismatchCategory.NOT_COMPARED);
        return response;
    }


    private boolean isExecutable(Wave0ReadPilotEntryPoint entryPoint) {
        return entryPoint.phase6c1Executable() || additionalExecutableEntryPoints.contains(entryPoint);
    }

    private <T extends Wave0CanonicalPayload> Wave0ReadPilotResponse<T> completeWithoutObservation(
            Wave0ReadPilotEntryPoint entryPoint,
            AuthorityDecision decision,
            Wave0ReadPilotGateState gateState,
            Invocation<T> legacy,
            String failureCode) {
        if (!legacy.success()) throw failure(failureCode, legacy);
        return new Wave0ReadPilotResponse<>(legacy.value(), entryPoint, decision, Wave0ReadPilotSource.LEGACY,
                false, false, null, gateState, clock.instant());
    }

    private <T extends Wave0CanonicalPayload> Wave0ReadPilotResponse<T> observeAndReturn(
            Wave0ReadPilotEntryPoint entryPoint,
            ActorContext actor,
            AuthorityDecision decision,
            Wave0ReadPilotGateStatus gate,
            Invocation<T> legacy,
            Invocation<T> target,
            Wave0ReadPilotSource servedBy,
            boolean shadowCompared,
            boolean fallbackUsed,
            Wave0ReadPilotMismatchCategory mismatch) {
        UUID observationId = UUID.randomUUID();
        Wave0ReadPilotObservation observation = new Wave0ReadPilotObservation(
                observationId,
                entryPoint,
                actor.tenantId(),
                decision.revision(),
                decision.mode(),
                decision.plane(),
                servedBy,
                shadowCompared,
                fallbackUsed,
                mismatch,
                legacy.fingerprint(),
                target.fingerprint(),
                legacy.durationMicros(),
                target.durationMicros(),
                legacy.errorCode(),
                target.errorCode(),
                actor.correlationId(),
                clock.instant());
        repository.saveObservation(observation);
        gates.evaluate(entryPoint, "system:wave0-observation-evaluation", actor.correlationId());
        Invocation<T> served = servedBy == Wave0ReadPilotSource.TARGET ? target : legacy;
        if (!served.success()) {
            Invocation<T> alternate = servedBy == Wave0ReadPilotSource.TARGET ? legacy : target;
            if (fallbackUsed && alternate.success()) served = alternate;
        }
        if (!served.success()) throw failure("WAVE0_READ_FAILED", served);
        return new Wave0ReadPilotResponse<>(served.value(), entryPoint, decision, servedBy,
                shadowCompared, fallbackUsed, observationId, gate.state(), clock.instant());
    }

    private static Wave0ReadPilotMismatchCategory compare(Invocation<?> legacy, Invocation<?> target) {
        if (!legacy.executed() && !target.executed()) return Wave0ReadPilotMismatchCategory.NOT_COMPARED;
        if (!legacy.success() && !target.success()) return Wave0ReadPilotMismatchCategory.BOTH_ERROR;
        if (!legacy.success()) return Wave0ReadPilotMismatchCategory.LEGACY_ERROR;
        if (!target.success()) return Wave0ReadPilotMismatchCategory.TARGET_ERROR;
        return legacy.value().compareTarget(target.value());
    }

    private static <T extends Wave0CanonicalPayload> Invocation<T> invoke(Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            T value = Objects.requireNonNull(supplier.get(), "read result");
            return Invocation.success(value, fingerprint(value.canonicalValue()), elapsedMicros(started));
        } catch (RuntimeException exception) {
            return Invocation.failure(errorCode(exception), elapsedMicros(started));
        }
    }

    private static long elapsedMicros(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000L);
    }

    private static String fingerprint(String canonicalValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof Wave0ReadPilotException pilot) return pilot.code();
        String name = exception.getClass().getSimpleName();
        return name == null || name.isBlank() ? "RUNTIME_EXCEPTION" : name.toUpperCase();
    }

    private static Wave0ReadPilotException failure(String code, Invocation<?> invocation) {
        return new Wave0ReadPilotException(code, "Wave 0 read failed: " + invocation.errorCode());
    }

    public record ActorContext(String tenantId, String actorId, String cohortKey, String correlationId) {
        public ActorContext {
            tenantId = normalize(tenantId, "INSTANCE");
            actorId = normalize(actorId, "unknown");
            cohortKey = normalize(cohortKey, actorId);
            correlationId = normalize(correlationId, UUID.randomUUID().toString());
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }

    private record Invocation<T extends Wave0CanonicalPayload>(
            boolean executed,
            T value,
            String fingerprint,
            long durationMicros,
            String errorCode) {
        private static <T extends Wave0CanonicalPayload> Invocation<T> success(T value, String fingerprint, long durationMicros) {
            return new Invocation<>(true, value, fingerprint, durationMicros, "");
        }
        private static <T extends Wave0CanonicalPayload> Invocation<T> failure(String errorCode, long durationMicros) {
            return new Invocation<>(true, null, "", durationMicros, errorCode);
        }
        private static <T extends Wave0CanonicalPayload> Invocation<T> notRun() {
            return new Invocation<>(false, null, "", 0, "");
        }
        private boolean success() { return executed && value != null && errorCode.isEmpty(); }
    }
}
