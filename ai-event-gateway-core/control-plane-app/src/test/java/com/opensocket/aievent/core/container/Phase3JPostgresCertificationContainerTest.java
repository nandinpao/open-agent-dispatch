package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class Phase3JPostgresCertificationContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_phase3")
            .withUsername("aeg")
            .withPassword("aeg");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() {
        DataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void phase3TablesAndFinalEvidenceLedgerMustExist() {
        for (String table : new String[]{
                "integration_webhook_inbox", "integration_external_issue_observations",
                "integration_projection_lanes", "integration_projection_lane_work",
                "integration_relay_topologies", "integration_provider_action_candidates",
                "integration_phase3_certification_runs", "integration_phase3_certification_evidence"}) {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name=?", Integer.class, table);
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    void certificationEvidenceMustBeAppendOnly() {
        jdbc.update("insert into integration_phase3_certification_runs(tenant_id,run_id,status,requested_by,created_at,updated_at,row_version) values ('000','run-1','NOT_CERTIFIED','test',now(),now(),1)");
        jdbc.update("insert into integration_phase3_certification_evidence(tenant_id,evidence_id,run_id,gate_id,status,artifact_path,artifact_sha256,created_at) values ('000','ev-1','run-1','SOURCE','PASSED','release/source.json',repeat('a',64),now())");
        assertThatThrownBy(() -> jdbc.update("update integration_phase3_certification_evidence set status='FAILED' where tenant_id='000' and evidence_id='ev-1'"))
                .hasMessageContaining("PHASE3_CERTIFICATION_EVIDENCE_APPEND_ONLY");
    }
}
