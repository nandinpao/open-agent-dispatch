package com.opensocket.aievent.core.productionclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PC-S6 real-PostgreSQL proof for bounded multi-replica claims, takeover and micro-batch benchmarking. */
@Tag("container")
@Tag("production-closure")
@Testcontainers(disabledWithoutDocker = true)
class PCStage6OperationalResilienceContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_pc_s6")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.execute("drop table if exists dispatch_requests,a2a_reconciliation_cases,a2a_remote_tracking_leases cascade");
            sql.execute("""
                    create table dispatch_requests(
                      dispatch_request_id text primary key,status text not null,claimed_by text,
                      updated_at timestamptz,created_at timestamptz not null,next_retry_at timestamptz,claim_until timestamptz)
                    """);
            sql.execute("""
                    create table a2a_reconciliation_cases(
                      case_id text primary key,status text not null,claimed_by text,claim_until timestamptz,
                      next_attempt_at timestamptz,updated_at timestamptz,created_at timestamptz not null)
                    """);
            sql.execute("""
                    create table a2a_remote_tracking_leases(
                      tracking_id text primary key,status text not null,lease_until timestamptz,next_poll_at timestamptz,
                      updated_at timestamptz not null)
                    """);
            sql.execute("create index idx_dispatch_requests_claim_scan_pc_s6 on dispatch_requests(updated_at asc nulls first,created_at asc) where status in('APPROVED','RETRY_WAITING','DISPATCHING')");
            sql.execute("create index idx_a2a_reconciliation_due_pc_s6 on a2a_reconciliation_cases((coalesce(next_attempt_at,created_at)),created_at) where status in('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING')");
            sql.execute("create index idx_a2a_reconciliation_executable_pc_s6 on a2a_reconciliation_cases(updated_at asc,created_at asc) where status='READY'");
            sql.execute("create index idx_a2a_tracking_due_pc_s6 on a2a_remote_tracking_leases((coalesce(next_poll_at,updated_at)),tracking_id) where status in('PENDING','ACTIVE','RECONCILING','CANCELING')");
        }
    }

    @Test
    void threeDispatchWorkersClaimDistinctRowsUnderContention() throws Exception {
        insertDispatchRows(48);
        List<List<String>> claims = concurrentClaims(3, worker -> claimDispatch(worker, 8));
        assertDistinctClaims(claims, 24);
    }

    @Test
    void threeReconciliationWorkersClaimDistinctRowsUnderContention() throws Exception {
        insertReconciliationRows(60, "READY", null, null);
        List<List<String>> claims = concurrentClaims(3, worker -> claimReconciliationExecutable(worker, 10));
        assertDistinctClaims(claims, 30);
    }

    @Test
    void expiredReconciliationOwnerCanBeTakenOverButLiveOwnerCannot() throws Exception {
        insertReconciliationRows(1, "EXECUTING", "node-a", OffsetDateTime.now().minusSeconds(5));
        assertThat(claimReconciliationDue("node-b", 1)).containsExactly("case-0000");
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("update a2a_reconciliation_cases set status='EXECUTING',claimed_by='node-c',claim_until=now()+interval '1 minute' where case_id='case-0000'");
        }
        assertThat(claimReconciliationDue("node-d", 1)).isEmpty();
    }

    @Test
    void microBatchHarnessExercisesOneFourEightAndSixteenWithoutChangingProductionDefault() throws Exception {
        for (int batch : List.of(1, 4, 8, 16)) {
            try (Connection c = connection(); Statement s = c.createStatement()) {
                s.execute("truncate table dispatch_requests");
            }
            insertDispatchRows(64);
            assertThat(claimDispatch("benchmark-" + batch, batch)).hasSize(batch);
        }
    }

    @Test
    void pcS6IndexesExistForAllThreeOperationalBacklogs() throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("""
                select indexname from pg_indexes where indexname in(
                  'idx_dispatch_requests_claim_scan_pc_s6','idx_a2a_reconciliation_due_pc_s6',
                  'idx_a2a_reconciliation_executable_pc_s6','idx_a2a_tracking_due_pc_s6') order by indexname
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> indexes = new ArrayList<>();
                while (rs.next()) indexes.add(rs.getString(1));
                assertThat(indexes).hasSize(4);
            }
        }
    }

    private List<List<String>> concurrentClaims(int workers, Claim claim) throws Exception {
        var pool = Executors.newFixedThreadPool(workers);
        try {
            CyclicBarrier barrier = new CyclicBarrier(workers);
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                String worker = "node-" + (char) ('a' + i);
                Callable<List<String>> task = () -> { barrier.await(); return claim.run(worker); };
                futures.add(pool.submit(task));
            }
            List<List<String>> result = new ArrayList<>();
            for (Future<List<String>> future : futures) result.add(future.get());
            return result;
        }
        finally {
            pool.shutdownNow();
        }
    }

    private List<String> claimDispatch(String worker, int limit) throws Exception {
        String sql = """
                with candidates as (
                  select dispatch_request_id from dispatch_requests
                   where status='APPROVED'
                      or (status='RETRY_WAITING' and (next_retry_at is null or next_retry_at<=now()))
                      or (status='DISPATCHING' and (claim_until is null or claim_until<=now()))
                   order by updated_at asc nulls first,created_at asc
                   for update skip locked limit ?
                )
                update dispatch_requests d set status='DISPATCHING',claimed_by=?,claim_until=now()+interval '30 seconds',updated_at=now()
                  from candidates c where d.dispatch_request_id=c.dispatch_request_id returning d.dispatch_request_id
                """;
        return claim(sql, limit, worker);
    }

    private List<String> claimReconciliationExecutable(String worker, int limit) throws Exception {
        String sql = """
                with due as (
                  select case_id from a2a_reconciliation_cases
                   where status='READY' and (claim_until is null or claim_until<=now())
                   order by updated_at,created_at for update skip locked limit ?
                )
                update a2a_reconciliation_cases c set status='EXECUTING',claimed_by=?,claim_until=now()+interval '30 seconds',updated_at=now()
                  from due where c.case_id=due.case_id returning c.case_id
                """;
        return claim(sql, limit, worker);
    }

    private List<String> claimReconciliationDue(String worker, int limit) throws Exception {
        String sql = """
                with due as (
                  select case_id from a2a_reconciliation_cases
                   where status in('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING')
                     and coalesce(next_attempt_at,created_at)<=now()
                     and (claim_until is null or claim_until<=now())
                   order by coalesce(next_attempt_at,created_at),created_at for update skip locked limit ?
                )
                update a2a_reconciliation_cases c set status='CLAIMED',claimed_by=?,claim_until=now()+interval '30 seconds',updated_at=now()
                  from due where c.case_id=due.case_id returning c.case_id
                """;
        return claim(sql, limit, worker);
    }

    private List<String> claim(String sql, int limit, String worker) throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            List<String> values = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit); ps.setString(2, worker);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) values.add(rs.getString(1)); }
            }
            c.commit();
            return values;
        }
    }

    private void insertDispatchRows(int count) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "insert into dispatch_requests values(?, 'APPROVED', null, ?, ?, null, null)")) {
            for (int i = 0; i < count; i++) {
                ps.setString(1, "dispatch-%04d".formatted(i));
                ps.setObject(2, OffsetDateTime.now().minusSeconds(count - i));
                ps.setObject(3, OffsetDateTime.now().minusSeconds(count - i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertReconciliationRows(int count, String status, String owner, OffsetDateTime claimUntil) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "insert into a2a_reconciliation_cases values(?,?,?,?,null,?,?)")) {
            for (int i = 0; i < count; i++) {
                OffsetDateTime created = OffsetDateTime.now().minusSeconds(count - i);
                ps.setString(1, "case-%04d".formatted(i));
                ps.setString(2, status);
                ps.setString(3, owner);
                ps.setObject(4, claimUntil);
                ps.setObject(5, created);
                ps.setObject(6, created);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void assertDistinctClaims(List<List<String>> claims, int expected) {
        Set<String> unique = new HashSet<>();
        claims.forEach(unique::addAll);
        assertThat(unique).hasSize(expected);
        assertThat(claims).allSatisfy(batch -> assertThat(batch).isNotEmpty());
    }

    private Connection connection() throws Exception {
        return java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @FunctionalInterface
    private interface Claim { List<String> run(String worker) throws Exception; }
}
