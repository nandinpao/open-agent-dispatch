package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PC-S3 / C0-C3 PostgreSQL proof for multi-replica remote lifecycle recovery.
 *
 * <p>The test deliberately uses three independent authority actors over one PostgreSQL database.
 * It proves takeover fencing, retry backoff, cancellation/reconciliation convergence, stream
 * switching, and exactly-once terminal CAS without introducing a second remote authority plane.</p>
 */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0C3MultiReplicaRemoteRecoveryContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0c3")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private A2ARemoteTaskTrackingService tracking;
    private A2ARemoteAuthorityService nodeA;
    private A2ARemoteAuthorityService nodeB;
    private A2ARemoteAuthorityService nodeC;

    @BeforeEach
    void setUp() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        named = new NamedParameterJdbcTemplate(ds);
        tracking = new A2ARemoteTaskTrackingService(named, "");
        nodeA = new A2ARemoteAuthorityService(named, "node-a");
        nodeB = new A2ARemoteAuthorityService(named, "node-b");
        nodeC = new A2ARemoteAuthorityService(named, "node-c");
        recreate();
        seed("track-a", "exec-a", "node-a", "token-a", 7L, "STREAM:track-a:7", "STREAM", "ACTIVE", null,
                OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now().minusSeconds(1));
    }

    @Test
    void expiredOwnerTakeoverFencesOldOwnerAcrossEpochAndStream() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease stale = tracking.findByTrackingId("tenant-a", "track-a");
        A2ARemoteAuthorityService.EventAuthorityContext staleContext = nodeA.context(stale, "STREAM");
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second',next_poll_at=now()-interval '1 second' where tracking_id='track-a'");

        List<A2ARemoteTaskTrackingService.RemoteTrackingLease> claimed = tracking.claimDue("tenant-a", "node-b", 1);
        assertThat(claimed).hasSize(1);
        A2ARemoteTaskTrackingService.RemoteTrackingLease current = claimed.get(0);
        assertThat(current.ownerInstanceId()).isEqualTo("node-b");
        assertThat(current.authorityEpoch()).isEqualTo(8L);
        assertThat(current.authoritativeStreamId()).isEqualTo("STREAM:track-a:8");

        A2ARemoteAuthorityService.AuthorityDecision staleDecision = nodeA.evaluate("tenant-a", "track-a", staleContext);
        assertThat(staleDecision.authoritative()).isFalse();
        assertThat(staleDecision.reason()).isIn("OWNER_MISMATCH", "AUTHORITY_EPOCH_STALE", "NON_AUTHORITATIVE_STREAM");
        assertThat(nodeB.evaluate("tenant-a", "track-a", nodeB.context(current, "STREAM")).authoritative()).isTrue();
    }

    @Test
    void explicitStreamToPollSwitchMakesOldStreamEvidenceOnly() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease stream = tracking.findByTrackingId("tenant-a", "track-a");
        A2ARemoteAuthorityService.EventAuthorityContext oldStream = nodeA.context(stream, "STREAM");
        A2ARemoteTaskTrackingService.RemoteTrackingLease poll = tracking.switchAuthority("tenant-a", stream, "POLL");

        assertThat(poll.authorityEpoch()).isEqualTo(8L);
        assertThat(poll.authoritativeStreamId()).isEqualTo("POLL:track-a:8");
        assertThat(nodeA.evaluate("tenant-a", "track-a", oldStream).authoritative()).isFalse();
        assertThat(nodeA.evaluate("tenant-a", "track-a", nodeA.context(poll, "POLL")).authoritative()).isTrue();
    }

    @Test
    void retryBackoffIsRespectedBeforeAnotherReplicaMayClaim() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease lease = tracking.findByTrackingId("tenant-a", "track-a");
        tracking.retry("tenant-a", lease, "TEMPORARY_REMOTE_FAILURE", 1);

        assertThat(jdbc.queryForObject("select status from a2a_remote_tracking_leases where tracking_id='track-a'", String.class))
                .isEqualTo("RECONCILING");
        assertThat(tracking.claimDue("tenant-a", "node-b", 1)).isEmpty();

        jdbc.update("update a2a_remote_tracking_leases set next_poll_at=now()-interval '1 second' where tracking_id='track-a'");
        List<A2ARemoteTaskTrackingService.RemoteTrackingLease> afterBackoff = tracking.claimDue("tenant-a", "node-b", 1);
        assertThat(afterBackoff).hasSize(1);
        assertThat(afterBackoff.get(0).ownerInstanceId()).isEqualTo("node-b");
    }

    @Test
    void nonTerminalCancelResponseMovesToReconciliationInsteadOfBlindResend() {
        jdbc.update("""
                update a2a_remote_tracking_leases
                   set status='CANCELING',cancellation_requested_at=now(),authoritative_stream_id='CANCEL:track-a:7'
                 where tracking_id='track-a'
                """);
        A2ARemoteTaskTrackingService.RemoteTrackingLease cancel = tracking.findByTrackingId("tenant-a", "track-a");
        assertThat(tracking.observed("tenant-a", cancel, "CANCEL", "TASK_STATE_RUNNING", false, true)).isTrue();

        assertThat(jdbc.queryForObject("select status from a2a_remote_tracking_leases where tracking_id='track-a'", String.class))
                .isEqualTo("RECONCILING");
        assertThat(jdbc.queryForObject("select tracking_status from a2a_remote_read_executions where execution_id='exec-a'", String.class))
                .isEqualTo("RECONCILING");
    }

    @Test
    void reconciliationThatStillSeesRunningRestoresPendingCancellationIntent() {
        jdbc.update("""
                update a2a_remote_tracking_leases
                   set status='RECONCILING',cancellation_requested_at=now(),owner_instance_id='node-b',lease_token='token-b',
                       lease_until=now()+interval '5 minutes',authority_epoch=8,authoritative_stream_id='POLL:track-a:8'
                 where tracking_id='track-a'
                """);
        A2ARemoteTaskTrackingService.RemoteTrackingLease poll = tracking.findByTrackingId("tenant-a", "track-a");
        assertThat(tracking.observed("tenant-a", poll, "POLL", "TASK_STATE_RUNNING", false, true)).isTrue();

        assertThat(jdbc.queryForObject("select status from a2a_remote_tracking_leases where tracking_id='track-a'", String.class))
                .isEqualTo("CANCELING");
        assertThat(jdbc.queryForObject("select tracking_status from a2a_remote_read_executions where execution_id='exec-a'", String.class))
                .isEqualTo("CANCELING");
    }

    @Test
    void threeReplicaClaimsChooseDistinctExpiredTrackingRows() throws Exception {
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second',next_poll_at=now()-interval '1 second' where tracking_id='track-a'");
        seed("track-b", "exec-b", null, null, 2L, null, "POLL", "ACTIVE", null,
                null, OffsetDateTime.now().minusSeconds(1));
        seed("track-c", "exec-c", null, null, 4L, null, "POLL", "RECONCILING", null,
                null, OffsetDateTime.now().minusSeconds(1));

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<String>> work = List.of(
                    claimTask("node-a", ready, go),
                    claimTask("node-b", ready, go),
                    claimTask("node-c", ready, go));
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : work) futures.add(pool.submit(task));
            ready.await();
            go.countDown();
            Set<String> trackingIds = new HashSet<>();
            for (Future<String> future : futures) trackingIds.add(future.get());
            assertThat(trackingIds).containsExactlyInAnyOrder("track-a", "track-b", "track-c");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateTerminalCasCanOnlyHaveOneWinner() throws Exception {
        A2ARemoteTaskTrackingService.RemoteTrackingLease lease = tracking.findByTrackingId("tenant-a", "track-a");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> c1 = terminalTask(lease, "evt-1", ready, go);
            Callable<Boolean> c2 = terminalTask(lease, "evt-2", ready, go);
            Future<Boolean> f1 = pool.submit(c1);
            Future<Boolean> f2 = pool.submit(c2);
            ready.await();
            go.countDown();
            assertThat(List.of(f1.get(), f2.get())).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("select lifecycle_version from a2a_remote_tracking_leases where tracking_id='track-a'", Long.class))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("select terminal_outcome from a2a_remote_tracking_leases where tracking_id='track-a'", String.class))
                    .isEqualTo("SUCCEEDED");
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> claimTask(String owner, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            List<A2ARemoteTaskTrackingService.RemoteTrackingLease> rows = tracking.claimDue("tenant-a", owner, 1);
            assertThat(rows).hasSize(1);
            return rows.get(0).trackingId();
        };
    }

    private Callable<Boolean> terminalTask(
            A2ARemoteTaskTrackingService.RemoteTrackingLease lease,
            String eventId,
            CountDownLatch ready,
            CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            return tracking.terminalize("tenant-a", lease, "TASK_STATE_COMPLETED", "SUCCEEDED", "STREAM", eventId).won();
        };
    }

    private void recreate() {
        jdbc.execute("drop table if exists a2a_remote_read_executions,a2a_remote_tracking_leases cascade");
        jdbc.execute("""
                create table a2a_remote_read_executions(
                  tenant_id text not null,execution_id text not null,status text,tracking_status text,remote_state text,
                  cancel_requested_at timestamptz,cancel_completed_at timestamptz,terminal_at timestamptz,completed_at timestamptz,
                  last_reconciled_at timestamptz,updated_at timestamptz,
                  remote_lifecycle_version bigint not null default 0,remote_terminal_outcome text,
                  remote_terminal_authority_epoch bigint,remote_terminal_stream_id text,
                  primary key(tenant_id,execution_id))
                """);
        jdbc.execute("""
                create table a2a_remote_tracking_leases(
                  tenant_id text not null,tracking_id text not null,execution_id text not null,delegation_id text,
                  remote_task_id text not null,peer_id text not null,interface_id text not null,tracking_mode text not null,
                  status text not null,push_config_id text,push_token_hash text,failure_count int not null default 0,
                  owner_instance_id text,lease_token text,lease_until timestamptz,next_poll_at timestamptz,
                  last_observed_state text,last_observed_at timestamptz,last_error text,last_remote_error_code text,
                  last_error_class text,last_error_disposition text,last_error_mapping_source text,last_error_mapping_override_id text,
                  authority_epoch bigint not null default 0,authoritative_stream_id text,authority_changed_at timestamptz,
                  lifecycle_version bigint not null default 0,lease_renewed_at timestamptz,lease_renewal_count bigint not null default 0,
                  cancellation_requested_at timestamptz,terminal_outcome text,terminal_source text,terminal_remote_state text,
                  terminal_authority_epoch bigint,terminal_stream_id text,terminal_journal_event_id text,terminalized_at timestamptz,
                  created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
                  primary key(tenant_id,tracking_id),unique(tenant_id,execution_id))
                """);
    }

    private void seed(
            String trackingId,
            String executionId,
            String owner,
            String token,
            long epoch,
            String stream,
            String mode,
            String status,
            OffsetDateTime cancellationRequestedAt,
            OffsetDateTime leaseUntil,
            OffsetDateTime nextPollAt) {
        jdbc.update("""
                insert into a2a_remote_read_executions(tenant_id,execution_id,status,tracking_status,updated_at)
                values('tenant-a',?,'WAITING_REMOTE',?,now())
                on conflict(tenant_id,execution_id) do nothing
                """, executionId, status);
        jdbc.update("""
                insert into a2a_remote_tracking_leases(
                  tenant_id,tracking_id,execution_id,delegation_id,remote_task_id,peer_id,interface_id,tracking_mode,status,
                  owner_instance_id,lease_token,lease_until,next_poll_at,authority_epoch,authoritative_stream_id,authority_changed_at,
                  cancellation_requested_at)
                values('tenant-a',?,?,?,?,'peer-a','if-a',?,?,?,?,?,?,?,?,now(),?)
                """, trackingId, executionId, "deleg-" + trackingId, "remote-" + trackingId, mode, status,
                owner, token, leaseUntil, nextPollAt, epoch, stream, cancellationRequestedAt);
    }

    private DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(POSTGRES.getDriverClassName());
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}
