package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.dispatch.DispatchEligibilityStatus;
import com.opensocket.aievent.core.dispatch.DispatchMethod;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchReviewMode;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteOutcome;
import com.opensocket.aievent.core.outbox.OutboxEventRecord;
import com.opensocket.aievent.core.outbox.OutboxEventRepository;
import com.opensocket.aievent.core.outbox.OutboxEventStatus;
import com.opensocket.aievent.database.persistence.adapter.converter.AdapterActionPersistenceConverter;
import com.opensocket.aievent.database.persistence.adapter.dao.AdapterActionDao;
import com.opensocket.aievent.database.persistence.adapter.repository.MybatisAdapterActionRepository;
import com.opensocket.aievent.database.persistence.domainevent.converter.OutboxEventPersistenceConverter;
import com.opensocket.aievent.database.persistence.domainevent.dao.OutboxEventDao;
import com.opensocket.aievent.database.persistence.domainevent.repository.MybatisOutboxEventRepository;
import com.opensocket.aievent.database.persistence.execution.converter.DispatchRequestPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.DispatchRequestDao;
import com.opensocket.aievent.database.persistence.execution.repository.MybatisDispatchRequestRepository;

import tools.jackson.databind.json.JsonMapper;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class PostgresClaimLeaseConcurrencyContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_core")
            .withUsername("aeg")
            .withPassword("aeg");

    private JdbcTemplate jdbc;
    private OutboxEventRepository outboxRepository;
    private DispatchRequestRepository dispatchRepository;
    private AdapterActionRepository adapterActionRepository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        jdbc = new JdbcTemplate(dataSource);
        MybatisContainerTestSupport mybatis = new MybatisContainerTestSupport(dataSource);
        outboxRepository = new MybatisOutboxEventRepository(
                mybatis.mapper(OutboxEventDao.class),
                new OutboxEventPersistenceConverter());
        dispatchRepository = new MybatisDispatchRequestRepository(
                mybatis.mapper(DispatchRequestDao.class),
                new DispatchRequestPersistenceConverter(JsonMapper.builder().build()));
        adapterActionRepository = new MybatisAdapterActionRepository(
                mybatis.mapper(AdapterActionDao.class),
                new AdapterActionPersistenceConverter(JsonMapper.builder().build()));
    }

    @Test
    void concurrentOutboxWorkersMustNotClaimTheSameRows() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        for (int index = 0; index < 20; index++) {
            outboxRepository.save(outbox("outbox-" + index, "event-" + index, createdAt.plusNanos(index)));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<List<OutboxEventRecord>> claims = runConcurrent(List.of(
                () -> outboxRepository.claimDispatchable(ClaimRequest.forLease("worker-a", now, Duration.ofSeconds(30), 20)),
                () -> outboxRepository.claimDispatchable(ClaimRequest.forLease("worker-b", now, Duration.ofSeconds(30), 20))));

        List<OutboxEventRecord> all = claims.stream().flatMap(List::stream).toList();
        Set<String> distinctIds = all.stream().map(OutboxEventRecord::getOutboxId).collect(Collectors.toSet());
        assertThat(all).hasSize(20);
        assertThat(distinctIds).hasSize(20);
    }

    @Test
    void staleOutboxWorkerMustNotFinalizeAReclaimedRow() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        outboxRepository.save(outbox("outbox-fence", "event-fence", now.minusMinutes(1)));

        OutboxEventRecord first = outboxRepository.claimDispatchable(
                ClaimRequest.forLease("worker-a", now, Duration.ofSeconds(5), 1)).getFirst();
        ClaimOwnership staleOwnership = new ClaimOwnership(first.getClaimedBy(), first.getClaimUntil());

        OffsetDateTime afterExpiry = first.getClaimUntil().plusSeconds(1);
        OutboxEventRecord second = outboxRepository.claimDispatchable(
                ClaimRequest.forLease("worker-b", afterExpiry, Duration.ofSeconds(30), 1)).getFirst();
        ClaimOwnership currentOwnership = new ClaimOwnership(second.getClaimedBy(), second.getClaimUntil());

        assertThat(outboxRepository.markPublished(
                second.getOutboxId(), currentOwnership, afterExpiry.plusSeconds(1)).applied()).isTrue();
        assertThat(outboxRepository.markRetry(
                first.getOutboxId(), staleOwnership, 1, afterExpiry.plusMinutes(1), "stale", afterExpiry.plusSeconds(2)).outcome())
                .isEqualTo(PersistenceWriteOutcome.OWNERSHIP_LOST);
    }

    @Test
    void concurrentDispatchWorkersMustClaimOnlyOnceAndFenceStaleCompletion() throws Exception {
        seedDispatchParents();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        dispatchRepository.save(dispatch("dispatch-1", createdAt));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ThrowingSupplier<DispatchRequest>> workers = List.of(
                () -> dispatchRepository.claimById(
                        "dispatch-1",
                        ClaimRequest.forLease("dispatch-worker-a", now, Duration.ofSeconds(5), 1)).orElse(null),
                () -> dispatchRepository.claimById(
                        "dispatch-1",
                        ClaimRequest.forLease("dispatch-worker-b", now, Duration.ofSeconds(5), 1)).orElse(null));

        List<DispatchRequest> claims = runConcurrent(workers).stream()
                .filter(java.util.Objects::nonNull)
                .toList();

        assertThat(claims).hasSize(1);
        DispatchRequest first = claims.getFirst();
        ClaimOwnership staleOwnership = new ClaimOwnership(first.getClaimedBy(), first.getClaimUntil());

        OffsetDateTime afterExpiry = first.getClaimUntil().plusSeconds(1);
        DispatchRequest reclaimed = dispatchRepository.claimById(
                "dispatch-1",
                ClaimRequest.forLease("dispatch-worker-c", afterExpiry, Duration.ofSeconds(30), 1)).orElseThrow();
        ClaimOwnership currentOwnership = new ClaimOwnership(reclaimed.getClaimedBy(), reclaimed.getClaimUntil());

        reclaimed.setStatus(DispatchRequestStatus.DISPATCHED);
        reclaimed.setDispatchedAt(afterExpiry.plusSeconds(1));
        reclaimed.setUpdatedAt(afterExpiry.plusSeconds(1));
        assertThat(dispatchRepository.saveClaimed(reclaimed, currentOwnership).applied()).isTrue();

        first.setStatus(DispatchRequestStatus.RETRY_WAITING);
        first.setUpdatedAt(afterExpiry.plusSeconds(2));
        assertThat(dispatchRepository.saveClaimed(first, staleOwnership).outcome())
                .isEqualTo(PersistenceWriteOutcome.OWNERSHIP_LOST);
    }

    private void seedDispatchParents() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                insert into incidents(
                    incident_id,fingerprint,tenant_id,source_system,severity,status,
                    first_seen_at,last_seen_at,occurrence_count,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """,
                "incident-1", "fp-1", "tenant-a", "MES", "HIGH", "ACTIVE",
                now, now, 1, now, now);
        jdbc.update("""
                insert into tasks(
                    task_id,incident_id,task_type,status,priority,tenant_id,routing_policy,
                    required_capabilities_json,occurrence_count_at_creation,created_at,updated_at)
                values (?,?,?,?,?,?,?,cast(? as jsonb),?,?,?)
                """,
                "task-1", "incident-1", "INCIDENT_RESPONSE", "ASSIGNED", "HIGH", "tenant-a",
                "AUTO", "[]", 1, now, now);
        jdbc.update("""
                insert into task_assignments(
                    assignment_id,task_id,incident_id,status,routing_policy,score,created_at,updated_at)
                values (?,?,?,?,?,?,?,?)
                """,
                "assignment-1", "task-1", "incident-1", "ASSIGNED", "AUTO", 100, now, now);
    }

    private OutboxEventRecord outbox(String outboxId, String eventId, OffsetDateTime createdAt) {
        OutboxEventRecord record = new OutboxEventRecord();
        record.setOutboxId(outboxId);
        record.setEventId(eventId);
        record.setEventType("TestEvent");
        record.setAggregateType("TestAggregate");
        record.setAggregateId("aggregate-1");
        record.setPayloadJson("{}");
        record.setStatus(OutboxEventStatus.PENDING);
        record.setCreatedAt(createdAt);
        record.setUpdatedAt(createdAt);
        return record;
    }

    private DispatchRequest dispatch(String id, OffsetDateTime createdAt) {
        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId(id);
        request.setAssignmentId("assignment-1");
        request.setTaskId("task-1");
        request.setIncidentId("incident-1");
        request.setStatus(DispatchRequestStatus.APPROVED);
        request.setReviewMode(DispatchReviewMode.AUTO_APPROVE);
        request.setEligibilityStatus(DispatchEligibilityStatus.ELIGIBLE);
        request.setDispatchMethod(DispatchMethod.INTERNAL_GATEWAY_HTTP);
        request.setGatewayDispatchPath("/internal/gateway/tasks/dispatch");
        request.setReason("container test");
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt);
        request.setApprovedAt(createdAt);
        return request;
    }

    @Test
    void concurrentAdapterActionWorkersMustClaimOnlyOnceAndFenceStaleCompletion() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        adapterActionRepository.save(adapterAction("adapter-action-1", createdAt));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ThrowingSupplier<AdapterAction>> workers = List.of(
                () -> adapterActionRepository.claimNext(
                        AdapterType.ISSUE_TRACKING,
                        ClaimRequest.forLease("adapter-worker-a", now, Duration.ofSeconds(5), 1)).orElse(null),
                () -> adapterActionRepository.claimNext(
                        AdapterType.ISSUE_TRACKING,
                        ClaimRequest.forLease("adapter-worker-b", now, Duration.ofSeconds(5), 1)).orElse(null));

        List<AdapterAction> claims = runConcurrent(workers).stream()
                .filter(java.util.Objects::nonNull)
                .toList();

        assertThat(claims).hasSize(1);
        AdapterAction first = claims.getFirst();
        ClaimOwnership staleOwnership = new ClaimOwnership(first.getClaimedBy(), first.getLeaseExpiresAt());

        OffsetDateTime afterExpiry = first.getLeaseExpiresAt().plusSeconds(1);
        AdapterAction reclaimed = adapterActionRepository.claimNext(
                AdapterType.ISSUE_TRACKING,
                ClaimRequest.forLease("adapter-worker-c", afterExpiry, Duration.ofSeconds(30), 1)).orElseThrow();
        ClaimOwnership currentOwnership = new ClaimOwnership(reclaimed.getClaimedBy(), reclaimed.getLeaseExpiresAt());

        reclaimed.setStatus(AdapterActionStatus.COMPLETED);
        reclaimed.setCompletedAt(afterExpiry.plusSeconds(1));
        reclaimed.setUpdatedAt(afterExpiry.plusSeconds(1));
        assertThat(adapterActionRepository.saveClaimed(reclaimed, currentOwnership, afterExpiry.plusSeconds(1)).applied()).isTrue();

        first.setStatus(AdapterActionStatus.RETRY_WAITING);
        first.setNextAttemptAt(afterExpiry.plusMinutes(1));
        first.setUpdatedAt(afterExpiry.plusSeconds(2));
        assertThat(adapterActionRepository.saveClaimed(first, staleOwnership, afterExpiry.plusSeconds(2)).outcome())
                .isEqualTo(PersistenceWriteOutcome.OWNERSHIP_LOST);
        assertThat(adapterActionRepository.findById("adapter-action-1").orElseThrow().getStatus())
                .isEqualTo(AdapterActionStatus.COMPLETED);
    }

    private AdapterAction adapterAction(String id, OffsetDateTime createdAt) {
        AdapterAction action = new AdapterAction();
        action.setActionId(id);
        action.setIdempotencyKey("idem-" + id);
        action.setIncidentId("incident-adapter-1");
        action.setTaskId("task-adapter-1");
        action.setAdapterName("issue-tracking");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(AdapterActionType.ISSUE_CREATE);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setReason("container lease test");
        action.setCreatedAt(createdAt);
        action.setUpdatedAt(createdAt);
        return action;
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private <T> List<T> runConcurrent(List<ThrowingSupplier<T>> suppliers) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(suppliers.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (ThrowingSupplier<T> supplier : suppliers) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return supplier.get();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(15, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
