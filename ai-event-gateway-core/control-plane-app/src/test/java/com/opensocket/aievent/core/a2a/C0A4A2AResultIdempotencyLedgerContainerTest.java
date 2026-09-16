package com.opensocket.aievent.core.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof that C0-A4 separates attempt evidence from canonical idempotency authority. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0A4A2AResultIdempotencyLedgerContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0a4").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc;

    @BeforeEach void setUp(){jdbc=new JdbcTemplate(dataSource());recreateSchema();}

    @Test void repeatedAttemptEvidenceWithSameKeyMustRemainAppendOnlyAndAllowed(){
        jdbc.update("insert into a2a_result_attempts values(?,?,?,?,now())","tenant-a","attempt-1","req-1","idem-1");
        jdbc.update("insert into a2a_result_attempts values(?,?,?,?,now())","tenant-a","attempt-2","req-1","idem-1");
        assertThat(jdbc.queryForObject("select count(*) from a2a_result_attempts where tenant_id='tenant-a' and idempotency_key='idem-1'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from a2a_result_idempotency_claims",Integer.class)).isZero();
    }

    @Test void acceptedCanonicalResultMustOwnSingleImmutableIdempotencyClaim(){
        jdbc.update("insert into a2a_result_attempts values(?,?,?,?,now())","tenant-a","attempt-1","req-1","idem-1");
        jdbc.update("insert into a2a_results values(?,?,?,?,?)","tenant-a","result-1","req-1","idem-1","fp-1");
        jdbc.update("insert into a2a_result_idempotency_claims values(?,?,?,?,?,?,now())","tenant-a","idem-1","req-1","result-1","attempt-1","fp-1");
        assertThat(jdbc.queryForObject("select a2a_result_id from a2a_result_idempotency_claims where tenant_id='tenant-a' and idempotency_key='idem-1'",String.class)).isEqualTo("result-1");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into a2a_result_idempotency_claims values(?,?,?,?,?,?,now())","tenant-a","idem-1","req-2","result-2",null,"fp-2"));
    }

    private void recreateSchema(){
        jdbc.execute("drop table if exists a2a_result_idempotency_claims,a2a_results,a2a_result_attempts cascade");
        jdbc.execute("create table a2a_result_attempts(tenant_id text not null,attempt_id text not null,a2a_request_id text not null,idempotency_key text not null,received_at timestamptz not null,primary key(tenant_id,attempt_id))");
        jdbc.execute("create index idx_a2a_result_attempts_idempotency_evidence on a2a_result_attempts(tenant_id,idempotency_key,received_at desc)");
        jdbc.execute("create table a2a_results(tenant_id text not null,a2a_result_id text not null,a2a_request_id text not null,idempotency_key text not null,result_fingerprint text not null,primary key(tenant_id,a2a_result_id),unique(tenant_id,idempotency_key),unique(tenant_id,a2a_request_id))");
        jdbc.execute("create table a2a_result_idempotency_claims(tenant_id text not null,idempotency_key text not null,a2a_request_id text not null,a2a_result_id text not null,acceptance_attempt_id text,result_fingerprint text not null,claimed_at timestamptz not null,primary key(tenant_id,idempotency_key),unique(tenant_id,a2a_result_id))");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
