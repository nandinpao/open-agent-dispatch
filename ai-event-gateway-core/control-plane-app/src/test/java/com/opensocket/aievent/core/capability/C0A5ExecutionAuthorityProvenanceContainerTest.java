package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchExecutionSafetyDecision;
import com.opensocket.aievent.core.dispatch.DispatchPreSendAdmissionDecision;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0A5ExecutionAuthorityProvenanceContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0a5").withUsername("opendispatch").withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private A0R7ExecutionLeaseDispatchSafetyGuard guard;
    private A0R7AtomicPreSendAdmission admission;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(ds);
        guard = new A0R7ExecutionLeaseDispatchSafetyGuard(named);
        admission = new A0R7AtomicPreSendAdmission(named, new DataSourceTransactionManager(ds));
        now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        recreateSchema();
    }

    @Test
    void explicitLegacyProvenanceMayBypassA0R7WithoutAssignmentLookup() {
        DispatchRequest legacy = new DispatchRequest();
        legacy.setTenantId("tenant-a");
        legacy.setDispatchRequestId("dispatch-legacy");
        legacy.setAssignmentId("assignment-legacy");
        legacy.setExecutionAuthorityVersion("LEGACY");
        legacy.setAuthorityProvenance(DispatchAuthorityProvenance.LEGACY_COMPATIBILITY);

        DispatchExecutionSafetyDecision safety = guard.evaluate(legacy, now);
        DispatchPreSendAdmissionDecision send = admission.admit(legacy, now);

        assertThat(safety.allowed()).isTrue();
        assertThat(send.applicable()).isFalse();
    }

    @Test
    void currentProvenanceWithMissingCanonicalEvidenceMustFailClosed() {
        DispatchRequest current = currentRequest();
        jdbc.update("insert into dispatch_requests(tenant_id,dispatch_request_id,assignment_id,execution_authority_version,canonical_execution_assignment_id,authority_provenance) values(?,?,?,?,?,?)", "tenant-a", "dispatch-current", "compat-current", "A0-R7-V206", "canonical-current", "A0_R7_CANONICAL");

        DispatchExecutionSafetyDecision safety = guard.evaluate(current, now);
        DispatchPreSendAdmissionDecision send = admission.admit(current, now);

        assertThat(safety.allowed()).isFalse();
        assertThat(safety.reasonCode()).isEqualTo("A0_R7_CANONICAL_AUTHORITY_EVIDENCE_MISSING");
        assertThat(send.allowed()).isFalse();
        assertThat(send.reasonCode()).isEqualTo("A0_R7_ATOMIC_AUTHORITY_EVIDENCE_MISSING");
    }

    private DispatchRequest currentRequest() {
        DispatchRequest r = new DispatchRequest();
        r.setTenantId("tenant-a");
        r.setDispatchRequestId("dispatch-current");
        r.setAssignmentId("compat-current");
        r.setExecutionAuthorityVersion("A0-R7-V206");
        r.setCanonicalExecutionAssignmentId("canonical-current");
        r.setAuthorityProvenance(DispatchAuthorityProvenance.A0_R7_CANONICAL);
        return r;
    }

    private void recreateSchema() {
        jdbc.execute("drop table if exists execution_dispatch_intents_v206,binding_authorization_envelopes,flow_routing_migration_state,execution_leases_v206,execution_assignments_v206,task_assignments,dispatch_requests cascade");
        jdbc.execute("create table dispatch_requests(tenant_id text,dispatch_request_id text,assignment_id text,execution_authority_version text,canonical_execution_assignment_id text,authority_provenance text,claimed_by text,claim_token text,claim_until timestamptz,outbox_status text)");
        jdbc.execute("create table task_assignments(tenant_id text,assignment_id text,execution_authority_version text,fencing_token text,canonical_execution_assignment_id text)");
        jdbc.execute("create table execution_assignments_v206(tenant_id text,assignment_id text,flow_id text,envelope_id text,binding_id text,execution_safety_mode text,lease_id text,fencing_token bigint,lease_until timestamptz,status text)");
        jdbc.execute("create table execution_leases_v206(tenant_id text,lease_id text,assignment_id text,status text,lease_until timestamptz)");
        jdbc.execute("create table flow_routing_migration_state(tenant_id text,flow_id text,migration_state text)");
        jdbc.execute("create table binding_authorization_envelopes(tenant_id text,envelope_id text,status text,valid_until timestamptz,admitted_binding_ids_json jsonb)");
        jdbc.execute("create table execution_dispatch_intents_v206(tenant_id text,intent_id text,assignment_id text,status text,external_execution_ref text,created_at timestamptz)");
    }

    private DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES.getJdbcUrl()); ds.setUsername(POSTGRES.getUsername()); ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}
