package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * C0-A1 PostgreSQL/JDBC regression proof for JSONB membership queries.
 *
 * <p>The contract deliberately executes through {@link NamedParameterJdbcTemplate} so PgJDBC sees
 * the same prepared-statement path used by the runtime services. PostgreSQL JSONB membership must
 * use {@code jsonb_exists(...)} instead of the {@code ?} operator, because the latter collides with
 * JDBC parameter markers.</p>
 */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0A1JdbcJsonbRuntimeContractContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0a1")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new NamedParameterJdbcTemplate(dataSource());
        jdbc.getJdbcTemplate().execute("drop table if exists c0a1_jsonb_runtime_contract");
        jdbc.getJdbcTemplate().execute("""
                create table c0a1_jsonb_runtime_contract (
                    binding_id text not null,
                    admitted_binding_ids_json jsonb not null,
                    operations_json jsonb not null,
                    supported_operations_json jsonb not null
                )
                """);
        jdbc.getJdbcTemplate().update("""
                insert into c0a1_jsonb_runtime_contract(
                    binding_id,
                    admitted_binding_ids_json,
                    operations_json,
                    supported_operations_json
                ) values (?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb))
                """, "binding-1", "[\"binding-1\",\"binding-2\"]", "[\"READ\",\"WRITE\"]", "[\"READ\"]");
    }

    @Test
    void columnValueMembershipShouldUseJsonbExistsWithoutJdbcPlaceholderCollision() {
        Boolean admitted = jdbc.queryForObject("""
                select jsonb_exists(admitted_binding_ids_json, binding_id)
                  from c0a1_jsonb_runtime_contract
                 where binding_id=:binding
                """, new MapSqlParameterSource("binding", "binding-1"), Boolean.class);

        assertThat(admitted).isTrue();
    }

    @Test
    void namedOperationMembershipShouldBindNormallyThroughPgJdbc() {
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from c0a1_jsonb_runtime_contract
                 where jsonb_exists(operations_json, cast(:op as text))
                """, new MapSqlParameterSource("op", "READ"), Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void supportedOperationsShouldPreserveEmptyMeansUnrestrictedContract() {
        Integer readCount = jdbc.queryForObject("""
                select count(*)
                  from c0a1_jsonb_runtime_contract
                 where supported_operations_json='[]'::jsonb
                    or jsonb_exists(supported_operations_json, cast(:op as text))
                """, new MapSqlParameterSource("op", "READ"), Integer.class);
        Integer writeCount = jdbc.queryForObject("""
                select count(*)
                  from c0a1_jsonb_runtime_contract
                 where supported_operations_json='[]'::jsonb
                    or jsonb_exists(supported_operations_json, cast(:op as text))
                """, new MapSqlParameterSource("op", "WRITE"), Integer.class);

        assertThat(readCount).isEqualTo(1);
        assertThat(writeCount).isZero();
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
