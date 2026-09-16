package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

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

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class Phase3R3WorkerConcurrencyContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_phase3_worker")
            .withUsername("aeg")
            .withPassword("aeg");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateAndSeed() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into integration_connections(tenant_id,connection_id,provider_type,connection_name,base_url,status,enabled,version,created_at,updated_at) values ('000','conn-r3','JIRA','R3 Jira','https://jira.invalid','ACTIVE',true,1,now(),now())");
        jdbc.update("insert into integration_projection_lanes(tenant_id,lane_id,aggregate_key,lane_type,connection_id,status,next_sequence,active_generation,max_queue_depth,queued_count,in_flight_count,row_version,created_at,updated_at) values ('000','lane-r3','TASK:T-1','TASK','conn-r3','ACTIVE',4,1,100,3,0,1,now(),now())");
        for (int sequence = 1; sequence <= 3; sequence++) {
            jdbc.update("insert into integration_projection_lane_work(tenant_id,work_id,lane_id,lane_sequence,generation,operation_type,status,aggregate_id,payload_hash,external_idempotency_marker,attempt_count,max_attempts,row_version,created_at,updated_at) values ('000',?,'lane-r3',?,1,?,'READY','T-1',repeat('a',64),?,0,8,1,now(),now())",
                    "work-" + sequence, sequence, sequence == 1 ? "CREATE" : "UPDATE", "marker-" + sequence);
        }
    }

    @Test
    void onlyLowestSequenceMayBeClaimedByConcurrentWorkers() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                String worker = "worker-" + index;
                tasks.add(() -> claimNext(worker));
            }
            List<String> claimed = executor.invokeAll(tasks).stream().map(future -> {
                try { return future.get(); } catch (Exception error) { throw new RuntimeException(error); }
            }).filter(value -> value != null).toList();
            assertThat(claimed).containsExactly("work-1");
            assertThat(jdbc.queryForObject("select count(*) from integration_projection_lane_work where tenant_id='000' and status='CLAIMED'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select status from integration_projection_lane_work where tenant_id='000' and work_id='work-2'", String.class)).isEqualTo("READY");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleLeaseMustReturnToRetryWaiting() {
        jdbc.update("update integration_projection_lane_work set status='CLAIMED',claim_owner='dead-worker',claim_token_hash=repeat('b',64),claim_until=now()-interval '10 seconds',attempt_count=1,row_version=row_version+1,updated_at=now() where tenant_id='000' and work_id='work-1'");
        int recovered = jdbc.update("update integration_projection_lane_work set status='RETRY_WAITING',claim_owner=null,claim_token_hash=null,claim_until=null,next_attempt_at=now(),last_error_code='STALE_CLAIM_RECOVERED',row_version=row_version+1,updated_at=now() where tenant_id='000' and status in ('CLAIMED','IN_PROGRESS','VERIFYING') and claim_until<now()");
        assertThat(recovered).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from integration_projection_lane_work where tenant_id='000' and work_id='work-1'", String.class)).isEqualTo("RETRY_WAITING");
    }

    private String claimNext(String owner) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            String sql = """
                    select candidate.work_id
                      from integration_projection_lane_work candidate
                     where candidate.tenant_id='000'
                       and candidate.status in ('READY','RETRY_WAITING')
                       and (candidate.next_attempt_at is null or candidate.next_attempt_at<=now())
                       and not exists (
                           select 1 from integration_projection_lane_work lower_work
                            where lower_work.tenant_id=candidate.tenant_id
                              and lower_work.lane_id=candidate.lane_id
                              and lower_work.generation=candidate.generation
                              and lower_work.lane_sequence<candidate.lane_sequence
                              and lower_work.status not in ('ACKNOWLEDGED','DEAD_LETTER','SUPERSEDED','CANCELLED'))
                     order by candidate.lane_sequence
                     for update skip locked
                     limit 1
                    """; // FOR UPDATE SKIP LOCKED is the cross-worker claim authority.
            String workId = null;
            try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
                if (result.next()) workId = result.getString(1);
            }
            if (workId != null) {
                try (PreparedStatement update = connection.prepareStatement("update integration_projection_lane_work set status='CLAIMED',claim_owner=?,claim_token_hash=repeat('c',64),claim_until=now()+interval '30 seconds',attempt_count=attempt_count+1,row_version=row_version+1,updated_at=now() where tenant_id='000' and work_id=?")) {
                    update.setString(1, owner); update.setString(2, workId); assertThat(update.executeUpdate()).isEqualTo(1);
                }
            }
            connection.commit();
            return workId;
        }
    }
}
