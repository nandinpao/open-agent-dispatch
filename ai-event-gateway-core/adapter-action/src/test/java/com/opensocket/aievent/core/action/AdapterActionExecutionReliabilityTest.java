package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionService;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterExecutorCircuitBreaker;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditService;
import com.opensocket.aievent.core.action.executor.audit.InMemoryAdapterExecutorAuditRepository;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentObservationCommand;

class AdapterActionExecutionReliabilityTest {

    @Test
    void retryableFailureShouldScheduleBackoffAndSkipUntilNextAttemptIsDue() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = pendingAction("act-retry-backoff");
        repository.save(action);

        AdapterActionExecutionProperties properties = executionProperties();
        properties.setInitialBackoff(Duration.ofSeconds(2));
        properties.setMaxBackoff(Duration.ofSeconds(5));
        AdapterActionExecutionService service = service(
                repository,
                properties,
                new ScriptedExecutor("retry-executor", a -> AdapterExecutionResult.retryableFailure("retry-executor", "temporary outage")));

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction retryWaiting = service.execute(action.getActionId());

        assertThat(retryWaiting.getStatus()).isEqualTo(AdapterActionStatus.RETRY_WAITING);
        assertThat(retryWaiting.getAttemptCount()).isEqualTo(1);
        assertThat(retryWaiting.getLastError()).isEqualTo("temporary outage");
        assertThat(retryWaiting.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(2));
        assertThat(service.executePending(10).getRequested()).isZero();
        assertThatThrownBy(() -> service.execute(action.getActionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("waiting for backoff");
    }

    @Test
    void retryableFailureShouldFailClosedAfterMaxAttempts() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = pendingAction("act-max-attempts");
        action.setAttemptCount(1);
        repository.save(action);

        AdapterActionExecutionProperties properties = executionProperties();
        properties.setMaxAttempts(2);
        AdapterActionExecutionService service = service(
                repository,
                properties,
                new ScriptedExecutor("retry-executor", a -> AdapterExecutionResult.retryableFailure("retry-executor", "still down")));

        AdapterAction failed = service.execute(action.getActionId());

        assertThat(failed.getStatus()).isEqualTo(AdapterActionStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(failed.getFailedAt()).isNotNull();
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    @Test
    void permanentFailureShouldNotRetryEvenWhenAttemptsRemain() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = pendingAction("act-permanent-failure");
        repository.save(action);

        AdapterActionExecutionService service = service(
                repository,
                executionProperties(),
                new ScriptedExecutor("issue-executor", a -> AdapterExecutionResult.permanentFailure("issue-executor", "401 unauthorized")));

        AdapterAction failed = service.execute(action.getActionId());

        assertThat(failed.getStatus()).isEqualTo(AdapterActionStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastError()).isEqualTo("401 unauthorized");
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    @Test
    void missingExecutorShouldBeMarkedUnavailableThenFailWhenRetryBudgetIsExhausted() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = pendingAction("act-no-executor");
        repository.save(action);

        AdapterActionExecutionProperties properties = executionProperties();
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofMillis(100));
        AdapterActionExecutionService service = service(repository, properties);

        AdapterAction unavailable = service.execute(action.getActionId());
        assertThat(unavailable.getStatus()).isEqualTo(AdapterActionStatus.EXECUTOR_UNAVAILABLE);
        assertThat(unavailable.getAttemptCount()).isEqualTo(1);
        assertThat(unavailable.getNextAttemptAt()).isNotNull();

        unavailable.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofMillis(1)));
        repository.save(unavailable);
        AdapterAction failed = service.execute(action.getActionId());

        assertThat(failed.getStatus()).isEqualTo(AdapterActionStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    @Test
    void manualRetryShouldResetFailedActionToPendingWithoutClearingAttemptAuditHistory() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = pendingAction("act-manual-retry");
        action.setStatus(AdapterActionStatus.FAILED);
        action.setAttemptCount(3);
        action.setLastError("permanent failure fixed by operator");
        action.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(action);

        AdapterAction retried = service(repository, executionProperties()).retry(action.getActionId());

        assertThat(retried.getStatus()).isEqualTo(AdapterActionStatus.PENDING);
        assertThat(retried.getAttemptCount()).isEqualTo(3);
        assertThat(retried.getLastError()).isNull();
        assertThat(retried.getNextAttemptAt()).isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void circuitBreakerShouldProtectExecutorAfterRepeatedRetryableFailures() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction first = pendingAction("act-circuit-1");
        AdapterAction second = pendingAction("act-circuit-2");
        repository.save(first);
        repository.save(second);

        AdapterActionExecutionProperties properties = executionProperties();
        properties.getCircuitBreaker().setFailureThreshold(1);
        properties.getCircuitBreaker().setOpenDuration(Duration.ofMinutes(1));
        AtomicInteger executions = new AtomicInteger();
        AdapterActionExecutionService service = service(
                repository,
                properties,
                new ScriptedExecutor("fragile-executor", action -> {
                    executions.incrementAndGet();
                    return AdapterExecutionResult.retryableFailure("fragile-executor", "upstream 503");
                }));

        AdapterAction retryWaiting = service.execute(first.getActionId());
        assertThat(retryWaiting.getStatus()).isEqualTo(AdapterActionStatus.RETRY_WAITING);

        AdapterAction protectedByCircuit = service.execute(second.getActionId());

        assertThat(protectedByCircuit.getStatus()).isEqualTo(AdapterActionStatus.EXECUTOR_UNAVAILABLE);
        assertThat(protectedByCircuit.getLastError()).contains("Executor circuit is open");
        assertThat(executions.get()).isEqualTo(1);
    }

    private AdapterActionExecutionService service(
            InMemoryAdapterActionRepository repository,
            AdapterActionExecutionProperties properties,
            AdapterActionExecutor... executors) {
        return new AdapterActionExecutionService(
                repository,
                List.of(executors),
                properties,
                new AdapterExecutorCircuitBreaker(properties),
                new AdapterExecutorAuditService(new InMemoryAdapterExecutorAuditRepository(), properties),
                new NoopIncidentFacade());
    }

    private AdapterActionExecutionProperties executionProperties() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.setMode("embedded");
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.setInitialBackoff(Duration.ofMillis(250));
        properties.setMaxBackoff(Duration.ofSeconds(5));
        return properties;
    }

    private AdapterAction pendingAction(String actionId) {
        AdapterAction action = new AdapterAction();
        action.setActionId(actionId);
        action.setIdempotencyKey("idem-" + actionId);
        action.setIncidentId("incident-" + actionId);
        action.setTaskId("task-" + actionId);
        action.setAdapterName("issue-tracking");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(AdapterActionType.ISSUE_CREATE);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        action.setUpdatedAt(action.getCreatedAt());
        return action;
    }

    private static final class ScriptedExecutor implements AdapterActionExecutor {
        private final String name;
        private final Function<AdapterAction, AdapterExecutionResult> behavior;

        private ScriptedExecutor(String name, Function<AdapterAction, AdapterExecutionResult> behavior) {
            this.name = name;
            this.behavior = behavior;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean supports(AdapterAction action) {
            return action.getAdapterType() == AdapterType.ISSUE_TRACKING;
        }

        @Override
        public AdapterExecutionResult execute(AdapterAction action) {
            return behavior.apply(action);
        }
    }

    private static final class NoopIncidentFacade implements IncidentFacade {
        @Override
        public Incident observe(IncidentObservationCommand command) {
            return new Incident();
        }

        @Override
        public Incident linkTaskIfAbsent(String incidentId, String taskId) {
            return new Incident();
        }

        @Override
        public Optional<Incident> findById(String incidentId) {
            return Optional.empty();
        }
    }
}
