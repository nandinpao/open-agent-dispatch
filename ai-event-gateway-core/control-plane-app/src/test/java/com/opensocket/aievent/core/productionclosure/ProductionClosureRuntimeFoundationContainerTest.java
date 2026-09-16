package com.opensocket.aievent.core.productionclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PC-S1 runtime-test foundation.
 *
 * <p>This is deliberately infrastructure-only: it proves that Production Closure
 * tests can run against real PostgreSQL semantics and that three independent Core
 * replica actors can safely contend for work using the same SKIP LOCKED primitive
 * used by production claim/reconciliation code. It does not certify Dispatch or
 * A2A business correctness; those proofs belong to PC-S2 and PC-S3.</p>
 */
@Tag("container")
@Tag("production-closure")
@Testcontainers(disabledWithoutDocker = true)
class ProductionClosureRuntimeFoundationContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_pc_s1")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    @BeforeAll
    static void migrateCanonicalSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void canonicalFlywayBaselineMustRetainV221AndAllowLaterProductionClosureMigrations() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select max(cast(version as integer)) from flyway_schema_history where success")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(221);
        }
    }

    @Test
    void threeReplicaActorsMustClaimDistinctRowsUsingSkipLocked() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists pc_s1_runtime_claim_probe");
            statement.execute("create table pc_s1_runtime_claim_probe (work_id integer primary key, claimed_by varchar(64))");
            statement.execute("insert into pc_s1_runtime_claim_probe(work_id) values (1),(2),(3)");
        }

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<Claim>> futures = new ArrayList<>();
            for (String replica : List.of("pc-s1-core-a", "pc-s1-core-b", "pc-s1-core-c")) {
                futures.add(pool.submit(() -> claimOne(replica, ready, start)));
            }
            ready.await();
            start.countDown();

            List<Claim> claims = new ArrayList<>();
            for (Future<Claim> future : futures) {
                claims.add(future.get());
            }

            assertThat(claims).hasSize(3);
            assertThat(claims).allSatisfy(claim -> {
                assertThat(claim.workId()).isBetween(1, 3);
                assertThat(claim.replicaId()).startsWith("pc-s1-core-");
            });
            Set<Integer> distinctWork = new HashSet<>();
            Set<String> distinctReplicas = new HashSet<>();
            claims.forEach(claim -> {
                distinctWork.add(claim.workId());
                distinctReplicas.add(claim.replicaId());
            });
            assertThat(distinctWork).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(distinctReplicas).containsExactlyInAnyOrder(
                    "pc-s1-core-a", "pc-s1-core-b", "pc-s1-core-c");
        } finally {
            pool.shutdownNow();
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("drop table if exists pc_s1_runtime_claim_probe");
            }
        }
    }

    private static Claim claimOne(
            String replicaId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         select work_id
                           from pc_s1_runtime_claim_probe
                          where claimed_by is null
                          order by work_id
                          for update skip locked
                          limit 1
                         """)) {
                assertThat(rows.next()).as("replica %s must claim work", replicaId).isTrue();
                int workId = rows.getInt(1);
                try (var update = connection.prepareStatement(
                        "update pc_s1_runtime_claim_probe set claimed_by=? where work_id=?")) {
                    update.setString(1, replicaId);
                    update.setInt(2, workId);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                connection.commit();
                return new Claim(replicaId, workId);
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record Claim(String replicaId, int workId) {}
}
