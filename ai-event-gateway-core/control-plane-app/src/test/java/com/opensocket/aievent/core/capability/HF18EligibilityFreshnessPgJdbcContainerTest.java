package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * HF18 regression proof for PgJDBC timestamptz handling in HOW eligibility freshness validation.
 *
 * <p>The pre-HF18 implementation used queryForMap() and cast generic JDBC values directly to
 * OffsetDateTime. PgJDBC may expose timestamptz through generic object retrieval as Timestamp,
 * causing a ClassCastException that was swallowed and misreported as ELIGIBILITY_EVIDENCE_STALE.
 * This test exercises the authoritative typed ResultSet#getObject(..., OffsetDateTime.class) path
 * against real PostgreSQL.</p>
 */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class HF18EligibilityFreshnessPgJdbcContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_hf18")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private NamedParameterJdbcTemplate jdbc;
    private ExecutionAdapterService service;

    @BeforeEach
    void setUp() {
        jdbc = new NamedParameterJdbcTemplate(dataSource());
        jdbc.getJdbcTemplate().execute("drop table if exists provider_eligibility_observations");
        jdbc.getJdbcTemplate().execute("""
                create table provider_eligibility_observations (
                    tenant_id text not null,
                    observation_id text not null,
                    observed_at timestamptz not null,
                    expires_at timestamptz,
                    primary key (tenant_id, observation_id)
                )
                """);
        service = new ExecutionAdapterService(jdbc, new ObjectMapper());
    }

    @Test
    void freshTimestamptzObservationShouldRemainValidThroughPgJdbcTypedRead() {
        OffsetDateTime now = OffsetDateTime.now();
        insert("tenant-hf18", "obs-fresh", now.minusSeconds(5), now.plusMinutes(5));

        assertThat(service.evaluateObservationFreshness("tenant-hf18", "obs-fresh", 300))
                .isEqualTo(ExecutionAdapterService.EligibilityFreshness.VALID);
    }

    @Test
    void expiredObservationShouldBeReportedAsExpiredNotStale() {
        OffsetDateTime now = OffsetDateTime.now();
        insert("tenant-hf18", "obs-expired", now.minusSeconds(30), now.minusSeconds(1));

        assertThat(service.evaluateObservationFreshness("tenant-hf18", "obs-expired", 300))
                .isEqualTo(ExecutionAdapterService.EligibilityFreshness.EXPIRED);
    }

    @Test
    void oldObservationShouldBeReportedAsStale() {
        OffsetDateTime now = OffsetDateTime.now();
        insert("tenant-hf18", "obs-stale", now.minusMinutes(10), null);

        assertThat(service.evaluateObservationFreshness("tenant-hf18", "obs-stale", 300))
                .isEqualTo(ExecutionAdapterService.EligibilityFreshness.STALE);
    }

    @Test
    void missingObservationShouldBeReportedAsMissing() {
        assertThat(service.evaluateObservationFreshness("tenant-hf18", "obs-missing", 300))
                .isEqualTo(ExecutionAdapterService.EligibilityFreshness.MISSING);
    }

    @Test
    void databaseFailureMustNotBeMasqueradedAsStale() {
        jdbc.getJdbcTemplate().execute("drop table provider_eligibility_observations");

        assertThatThrownBy(() -> service.evaluateObservationFreshness("tenant-hf18", "obs-any", 300))
                .isInstanceOf(DataAccessException.class);
    }

    private void insert(String tenant, String observationId, OffsetDateTime observedAt, OffsetDateTime expiresAt) {
        jdbc.getJdbcTemplate().update(
                "insert into provider_eligibility_observations(tenant_id,observation_id,observed_at,expires_at) values (?,?,?,?)",
                tenant, observationId, observedAt, expiresAt);
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
