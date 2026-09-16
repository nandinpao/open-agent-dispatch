package com.opensocket.aievent.core.enforcement.activation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0AdminSummaryView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMetricSummary;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMismatchCategory;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotObservation;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotData;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;
import com.opensocket.aievent.core.enforcement.activation.core.UnifiedEnforcementKernel;

class Wave0ReadPilotServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Wave0ReadPilotService.ActorContext ACTOR =
            new Wave0ReadPilotService.ActorContext("INSTANCE", "pilot-user", "pilot-user", "corr-wave0");

    @Test
    void shadowReturnsLegacyAndRecordsMatchingComparison() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.NON_SENSITIVE_ADMIN, true, 10_000, 10_000);
        Wave0ReadPilotService service = service(repository, route(Wave0ReadPilotEntryPoint.NON_SENSITIVE_ADMIN, AuthorityMode.SHADOW, 0, Set.of()));
        Wave0AdminSummaryView value = payload(1);

        Wave0ReadPilotResponse<Wave0AdminSummaryView> response = service.execute(
                Wave0ReadPilotEntryPoint.NON_SENSITIVE_ADMIN, ACTOR, () -> value, () -> value);

        assertEquals(Wave0ReadPilotSource.LEGACY, response.servedBy());
        assertTrue(response.shadowCompared());
        assertFalse(response.fallbackUsed());
        assertTrue(response.observationId() != null);
        assertEquals(Wave0ReadPilotMismatchCategory.MATCH, repository.observations.getFirst().mismatchCategory());
    }

    @Test
    void canaryTargetFailureFallsBackAndAutoPausesOnErrorBudget() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, true, 10_000, 0);
        repository.gates.put(Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, new Wave0ReadPilotGateStatus(
                Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, Wave0ReadPilotGateState.OPEN,
                "TEST_OPEN", "test", NOW, 2));
        Wave0ReadPilotService service = service(repository, route(
                Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, AuthorityMode.TARGET_CANARY, 1, Set.of("pilot-user")));

        Wave0ReadPilotResponse<Wave0AdminSummaryView> response = service.execute(
                Wave0ReadPilotEntryPoint.PERMISSION_CATALOG,
                ACTOR,
                () -> payload(2),
                () -> { throw new IllegalStateException("target unavailable"); });

        assertEquals(Wave0ReadPilotSource.LEGACY, response.servedBy());
        assertTrue(response.fallbackUsed());
        assertEquals(Wave0ReadPilotMismatchCategory.TARGET_ERROR, repository.observations.getFirst().mismatchCategory());
        assertEquals(Wave0ReadPilotGateState.PAUSED, repository.gate(Wave0ReadPilotEntryPoint.PERMISSION_CATALOG).state());
    }


    @Test
    void observingGateNeverServesCanaryTarget() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, true, 10_000, 10_000);
        Wave0ReadPilotService service = service(repository, route(
                Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, AuthorityMode.TARGET_CANARY, 1, Set.of("pilot-user")));

        Wave0ReadPilotResponse<Wave0AdminSummaryView> response = service.execute(
                Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, ACTOR, () -> payload(7), () -> payload(8));

        assertEquals(Wave0ReadPilotSource.LEGACY, response.servedBy());
        assertTrue(response.shadowCompared());
        assertFalse(response.fallbackUsed());
        assertEquals(7, response.payload().publishedAuthorityRevisions());
    }

    @Test
    void targetPrimaryIsRejectedAndFailsClosedToLegacy() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.ENFORCEMENT_RUNTIME_STATUS, true, 10_000, 10_000);
        Wave0ReadPilotService service = service(repository, route(
                Wave0ReadPilotEntryPoint.ENFORCEMENT_RUNTIME_STATUS, AuthorityMode.TARGET_PRIMARY, 10_000, Set.of()));

        Wave0ReadPilotResponse<Wave0AdminSummaryView> response = service.execute(
                Wave0ReadPilotEntryPoint.ENFORCEMENT_RUNTIME_STATUS, ACTOR, () -> payload(3), () -> payload(4));

        assertEquals(Wave0ReadPilotSource.LEGACY, response.servedBy());
        assertEquals(3, response.payload().publishedAuthorityRevisions());
        assertEquals(Wave0ReadPilotMismatchCategory.UNSUPPORTED_MODE, repository.observations.getFirst().mismatchCategory());
    }

    @Test
    void taskListSearchRemainsStructurallyNonExecutable() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH, true, 10_000, 10_000);
        Wave0ReadPilotService service = service(repository, route(
                Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH, AuthorityMode.TARGET_CANARY, 9_999, Set.of("pilot-user")));

        Wave0ReadPilotResponse<Wave0AdminSummaryView> response = service.execute(
                Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH, ACTOR, () -> payload(5), () -> payload(6));

        assertEquals(Wave0ReadPilotSource.LEGACY, response.servedBy());
        assertEquals(5, response.payload().publishedAuthorityRevisions());
        assertTrue(repository.observations.isEmpty());
    }

    @Test
    void disabledExecutionPropertyPreventsManualResume() {
        InMemoryRepository repository = repository(Wave0ReadPilotEntryPoint.READINESS_EVIDENCE, true, 10_000, 10_000);
        Wave0ReadPilotGateService gates = new Wave0ReadPilotGateService(repository, CLOCK, false);
        assertThrows(Wave0ReadPilotException.class, () -> gates.resume(
                Wave0ReadPilotEntryPoint.READINESS_EVIDENCE, 1, "Resume only after certification evidence is verified.",
                "operator", "corr-resume"));
    }

    private static Wave0AdminSummaryView payload(long value) {
        return new Wave0AdminSummaryView(value, value, value, value, 0, NOW);
    }

    private static Wave0ReadPilotService service(InMemoryRepository repository, AuthorityRouteDefinition route) {
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        router.publish(new AuthoritySnapshotData(1, NOW, "", List.of(route)));
        UnifiedEnforcementKernel kernel = new UnifiedEnforcementKernel(context ->
                com.opensocket.aievent.core.enforcement.activation.contract.HardGuardDecision.allow("TEST_ALLOW"), router);
        Wave0ReadPilotGateService gates = new Wave0ReadPilotGateService(repository, CLOCK, true);
        return new Wave0ReadPilotService(kernel, repository, gates, CLOCK, true);
    }

    private static AuthorityRouteDefinition route(
            Wave0ReadPilotEntryPoint entryPoint,
            AuthorityMode mode,
            int basisPoints,
            Set<String> includes) {
        return new AuthorityRouteDefinition(
                entryPoint.routingContext("INSTANCE", "pilot-user", "corr").routeKey(),
                mode,
                basisPoints,
                includes,
                Set.of(),
                "PHASE6C1_TEST");
    }

    private static InMemoryRepository repository(
            Wave0ReadPilotEntryPoint entryPoint,
            boolean autoPause,
            int maximumMismatchBasisPoints,
            int maximumTargetErrorBasisPoints) {
        InMemoryRepository repository = new InMemoryRepository();
        repository.policies.put(entryPoint, new Wave0ReadPilotPolicy(
                entryPoint, true, true, 1, maximumMismatchBasisPoints, maximumTargetErrorBasisPoints,
                10_000, Duration.ofMinutes(15), autoPause, 1));
        repository.gates.put(entryPoint, new Wave0ReadPilotGateStatus(
                entryPoint, Wave0ReadPilotGateState.OBSERVING, "TEST_OBSERVING", "test", NOW, 1));
        return repository;
    }

    private static final class InMemoryRepository implements Wave0ReadPilotRepository {
        private final Map<Wave0ReadPilotEntryPoint, Wave0ReadPilotPolicy> policies = new EnumMap<>(Wave0ReadPilotEntryPoint.class);
        private final Map<Wave0ReadPilotEntryPoint, Wave0ReadPilotGateStatus> gates = new EnumMap<>(Wave0ReadPilotEntryPoint.class);
        private final List<Wave0ReadPilotObservation> observations = new ArrayList<>();

        @Override public Wave0ReadPilotPolicy policy(Wave0ReadPilotEntryPoint entryPoint) { return policies.get(entryPoint); }
        @Override public List<Wave0ReadPilotPolicy> policies() { return List.copyOf(policies.values()); }
        @Override public Wave0ReadPilotGateStatus gate(Wave0ReadPilotEntryPoint entryPoint) { return gates.get(entryPoint); }
        @Override public List<Wave0ReadPilotGateStatus> gates() { return List.copyOf(gates.values()); }
        @Override public void saveObservation(Wave0ReadPilotObservation observation) { observations.add(observation); }
        @Override public List<Wave0ReadPilotObservation> observations(Wave0ReadPilotEntryPoint entryPoint, int limit) {
            return observations.stream().filter(item -> item.entryPoint() == entryPoint).limit(limit).toList();
        }
        @Override public Wave0ReadPilotMetricSummary metrics(Wave0ReadPilotEntryPoint entryPoint, Instant startedAt, Instant evaluatedAt) {
            List<Wave0ReadPilotObservation> values = observations.stream().filter(item -> item.entryPoint() == entryPoint).toList();
            long compared = values.stream().filter(Wave0ReadPilotObservation::shadowCompared).count();
            long mismatches = values.stream().filter(item -> item.mismatchCategory() == Wave0ReadPilotMismatchCategory.PAYLOAD_MISMATCH).count();
            long targetErrors = values.stream().filter(item -> item.mismatchCategory() == Wave0ReadPilotMismatchCategory.TARGET_ERROR || item.mismatchCategory() == Wave0ReadPilotMismatchCategory.BOTH_ERROR).count();
            int mismatchBps = values.isEmpty() ? 0 : (int)(mismatches * 10_000 / values.size());
            int errorBps = values.isEmpty() ? 0 : (int)(targetErrors * 10_000 / values.size());
            long p95 = values.stream().mapToLong(item -> item.targetDurationMicros() / 1_000).max().orElse(0);
            return new Wave0ReadPilotMetricSummary(entryPoint, values.size(), compared, mismatches, targetErrors,
                    mismatchBps, errorBps, p95, startedAt, evaluatedAt);
        }
        @Override public Wave0ReadPilotGateStatus transition(
                Wave0ReadPilotEntryPoint entryPoint,
                long expectedVersion,
                Wave0ReadPilotGateState state,
                String reasonCode,
                String actorId,
                String correlationId,
                Instant changedAt) {
            Wave0ReadPilotGateStatus current = gates.get(entryPoint);
            if (current.version() != expectedVersion) throw new IllegalStateException("optimistic version mismatch");
            Wave0ReadPilotGateStatus next = new Wave0ReadPilotGateStatus(
                    entryPoint, state, reasonCode, actorId, changedAt, expectedVersion + 1);
            gates.put(entryPoint, next);
            return next;
        }
    }
}
