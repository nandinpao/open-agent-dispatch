package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentObservationCommand;

import org.junit.jupiter.api.Test;

class AdapterExternalWorkerContractTest {
    @Test
    void workerCanClaimHeartbeatAndCompleteAction() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterActionService service = service(repository);
        repository.save(action("act-1", AdapterType.MCP));

        AdapterAction claimed = service.claimNext(AdapterType.MCP, "worker-mcp-001", Duration.ofSeconds(60)).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(AdapterActionStatus.CLAIMED);
        assertThat(claimed.getClaimedBy()).isEqualTo("worker-mcp-001");
        assertThat(claimed.getLeaseExpiresAt()).isNotNull();

        AdapterAction heartbeat = service.heartbeat(claimed.getActionId(), "worker-mcp-001", Duration.ofSeconds(120));
        assertThat(heartbeat.getStatus()).isEqualTo(AdapterActionStatus.CLAIMED);
        assertThat(heartbeat.getWorkerHeartbeatAt()).isNotNull();

        AdapterAction completed = service.completeByWorker(claimed.getActionId(), "worker-mcp-001", "mcp-response-1");
        assertThat(completed.getStatus()).isEqualTo(AdapterActionStatus.COMPLETED);
        assertThat(completed.getClaimedBy()).isNull();
        assertThat(completed.getLeaseExpiresAt()).isNull();
        assertThat(completed.getResponseRef()).isEqualTo("mcp-response-1");
    }

    @Test
    void anotherWorkerCannotCompleteClaimedAction() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterActionService service = service(repository);
        repository.save(action("act-1", AdapterType.ISSUE_TRACKING));

        AdapterAction claimed = service.claimNext(AdapterType.ISSUE_TRACKING, "worker-issue-001", Duration.ofSeconds(60)).orElseThrow();

        assertThatThrownBy(() -> service.completeByWorker(claimed.getActionId(), "worker-issue-002", "wrong-worker"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claimed by another worker");
        assertThat(repository.findById(claimed.getActionId()).orElseThrow().getStatus()).isEqualTo(AdapterActionStatus.CLAIMED);
    }

    @Test
    void expiredClaimCanBeClaimedByAnotherWorker() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = action("act-1", AdapterType.MCP);
        action.setStatus(AdapterActionStatus.CLAIMED);
        action.setClaimedBy("dead-worker");
        action.setClaimedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        action.setLeaseExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        repository.save(action);
        AdapterActionService service = service(repository);

        AdapterAction claimed = service.claimNext(AdapterType.MCP, "worker-mcp-002", Duration.ofSeconds(60)).orElseThrow();

        assertThat(claimed.getStatus()).isEqualTo(AdapterActionStatus.CLAIMED);
        assertThat(claimed.getClaimedBy()).isEqualTo("worker-mcp-002");
    }

    @Test
    void retryableWorkerFailureShouldMoveActionToRetryWaiting() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterActionService service = service(repository);
        AdapterAction action = action("act-retryable", AdapterType.ISSUE_TRACKING);
        action.setMaxAttempts(3);
        repository.save(action);

        AdapterAction claimed = service.claimNext(AdapterType.ISSUE_TRACKING, "issue-worker-001", Duration.ofSeconds(60)).orElseThrow();
        AdapterAction failed = service.failByWorker(claimed.getActionId(), "issue-worker-001", "Redmine timeout", true);

        assertThat(failed.getStatus()).isEqualTo(AdapterActionStatus.RETRY_WAITING);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isNotNull();
        assertThat(failed.getClaimedBy()).isNull();
        assertThat(failed.getLeaseExpiresAt()).isNull();
    }

    @Test
    void persistedClaimedActionShouldRestoreOwnershipFence() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = action("act-persisted-claim", AdapterType.MCP);
        OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        action.setStatus(AdapterActionStatus.CLAIMED);
        action.setClaimedBy("persisted-worker");
        action.setClaimedAt(expiredAt.minusMinutes(4));
        action.setLeaseExpiresAt(expiredAt);
        repository.save(action);

        AdapterAction recovered = service(repository).recoverExpiredWorkerLease(action.getActionId());

        assertThat(recovered.getStatus()).isEqualTo(AdapterActionStatus.RETRY_WAITING);
        assertThat(recovered.getClaimedBy()).isNull();
        assertThat(recovered.getLeaseExpiresAt()).isNull();
    }

    @Test
    void expiredWorkerLeaseRecoveryShouldRetryOrFailByAttemptBudget() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterAction action = action("act-expired-recovery", AdapterType.MCP);
        action.setStatus(AdapterActionStatus.CLAIMED);
        action.setClaimedBy("dead-worker");
        action.setClaimedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        action.setLeaseExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        action.setMaxAttempts(3);
        repository.save(action);
        AdapterActionService service = service(repository);

        AdapterAction recovered = service.recoverExpiredWorkerLease(action.getActionId());

        assertThat(recovered.getStatus()).isEqualTo(AdapterActionStatus.RETRY_WAITING);
        assertThat(recovered.getLastError()).contains("External worker lease expired");
        assertThat(recovered.getClaimedBy()).isNull();
        assertThat(recovered.getNextAttemptAt()).isNotNull();
    }

    private AdapterActionService service(InMemoryAdapterActionRepository repository) {
        return new AdapterActionService(repository, incidentFacade(), new AdapterActionProperties());
    }

    private IncidentFacade incidentFacade() {
        return new IncidentFacade() {
            @Override
            public Incident observe(IncidentObservationCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Incident linkTaskIfAbsent(String incidentId, String taskId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Incident> findById(String incidentId) {
                return Optional.empty();
            }
        };
    }

    private AdapterAction action(String id, AdapterType adapterType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = new AdapterAction();
        action.setActionId(id);
        action.setIdempotencyKey("idem-" + id);
        action.setAdapterName(adapterType == AdapterType.MCP ? "mcp-default" : "issue-default");
        action.setAdapterType(adapterType);
        action.setActionType(adapterType == AdapterType.MCP ? AdapterActionType.MCP_CONTEXT_FETCH : AdapterActionType.ISSUE_CREATE);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        return action;
    }
}
