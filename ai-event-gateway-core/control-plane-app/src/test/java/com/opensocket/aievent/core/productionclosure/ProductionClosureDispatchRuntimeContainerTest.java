package com.opensocket.aievent.core.productionclosure;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.capability.A0R7AtomicPreSendAdmission;
import com.opensocket.aievent.core.capability.A0R7DispatchRecoveryAuthority;
import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchPreSendAdmissionDecision;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryAuthorityAction;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.NettyDispatchCommand;
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

/** PC-S2 real-PostgreSQL proof for D01-D08 Dispatch authority/recovery semantics. */
@Tag("container")
@Tag("production-closure")
@Testcontainers(disabledWithoutDocker = true)
class ProductionClosureDispatchRuntimeContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_pc_s2")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private A0R7AtomicPreSendAdmission admission;
    private A0R7DispatchRecoveryAuthority recovery;
    private OffsetDateTime now;
    private OffsetDateTime claimUntil;

    @BeforeEach
    void setup() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        named = new NamedParameterJdbcTemplate(ds);
        admission = new A0R7AtomicPreSendAdmission(named, new DataSourceTransactionManager(ds));
        recovery = new A0R7DispatchRecoveryAuthority(named);
        now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        claimUntil = now.plusMinutes(5);
        recreateSchema();
        seed();
    }

    @Test void d01NormalDispatchMustCommitSendStarted() {
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isTrue();
        assertThat(status()).isEqualTo("SEND_STARTED");
    }

    @Test void d02ExpiredLeaseMustBlockBeforeSendStarted() {
        jdbc.update("update execution_leases_v206 set lease_until=? where lease_id='lease-1'", now.minusSeconds(1));
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("A0_R7_EXECUTION_LEASE_EXPIRED");
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d03StaleFenceMustBlockBeforeSendStarted() {
        jdbc.update("update task_assignments set fencing_token='6' where assignment_id='compat-assignment-1'");
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("A0_R7_COMPATIBILITY_FENCE_MISMATCH");
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d04RevokedEnvelopeMustBlockBeforeSendStarted() {
        jdbc.update("update binding_authorization_envelopes set status='REVOKED' where envelope_id='envelope-1'");
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("A0_R7_BINDING_ENVELOPE_NOT_ACTIVE");
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d05FlowDowngradeMustBlockBeforeSendStarted() {
        jdbc.update("update flow_routing_migration_state set migration_state='SHADOW' where flow_id='flow-1'");
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("A0_R7_FLOW_NOT_NEW_AUTHORITATIVE");
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d06IntentMismatchMustBlockBeforeSendStarted() {
        jdbc.update("update execution_dispatch_intents_v206 set external_execution_ref='dispatch:other' where intent_id='intent-1'");
        DispatchPreSendAdmissionDecision d = admission.admit(request(), now);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("A0_R7_DISPATCH_INTENT_NOT_HANDED_OFF");
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d07CrashBeforeSendStartedRemainsSafeForOrdinaryExpiredClaimRecovery() {
        var decision = recovery.reconcile(request(), now.plusMinutes(10));
        assertThat(decision.applicable()).isFalse();
        assertThat(status()).isEqualTo("HANDED_OFF");
    }

    @Test void d08CrashAfterSendStartedMustBecomeDeliveryUnknownAndNeverBlindRetry() {
        assertThat(admission.admit(request(), now).allowed()).isTrue();
        var decision = recovery.reconcile(request(), now.plusMinutes(10));
        assertThat(decision.applicable()).isTrue();
        assertThat(decision.action()).isEqualTo(DispatchRecoveryAuthorityAction.HOLD_DELIVERY_UNKNOWN);
        assertThat(status()).isEqualTo("DELIVERY_UNKNOWN");
        assertThat(jdbc.queryForObject(
                "select count(*) from execution_dispatch_intent_events_v206 where reason_code='PC_S2_EXPIRED_CLAIM_AFTER_SEND_STARTED'",
                Integer.class)).isEqualTo(1);
    }

    private String status() {
        return jdbc.queryForObject("select status from execution_dispatch_intents_v206 where intent_id='intent-1'", String.class);
    }

    private DispatchRequest request() {
        DispatchRequest request = new DispatchRequest();
        request.setTenantId("tenant-a");
        request.setDispatchRequestId("dispatch-1");
        request.setAssignmentId("compat-assignment-1");
        request.setExecutionAuthorityVersion("A0-R7-V206");
        request.setCanonicalExecutionAssignmentId("canonical-assignment-1");
        request.setAuthorityProvenance(DispatchAuthorityProvenance.A0_R7_CANONICAL);
        request.setTaskId("task-1");
        request.setAttemptCount(1);
        request.setClaimedBy("worker-a");
        request.setClaimToken("claim-token-1");
        request.setClaimUntil(claimUntil);
        NettyDispatchCommand command = new NettyDispatchCommand();
        command.setFencingToken("7");
        request.setCommand(command);
        return request;
    }

    private void recreateSchema() {
        jdbc.execute("drop table if exists execution_dispatch_intent_events_v206, execution_dispatch_intents_v206, binding_authorization_envelopes, flow_routing_migration_state, execution_leases_v206, execution_assignments_v206, task_assignments, dispatch_requests cascade");
        jdbc.execute("create table dispatch_requests(tenant_id text,dispatch_request_id text,assignment_id text,execution_authority_version text,canonical_execution_assignment_id text,authority_provenance text,claimed_by text,claim_token text,claim_until timestamptz,outbox_status text)");
        jdbc.execute("create table task_assignments(tenant_id text,assignment_id text,execution_authority_version text,fencing_token text,canonical_execution_assignment_id text)");
        jdbc.execute("create table execution_assignments_v206(tenant_id text,assignment_id text,flow_id text,envelope_id text,binding_id text,execution_safety_mode text,lease_id text,fencing_token bigint,lease_until timestamptz,status text)");
        jdbc.execute("create table execution_leases_v206(tenant_id text,lease_id text,assignment_id text,status text,lease_until timestamptz)");
        jdbc.execute("create table flow_routing_migration_state(tenant_id text,flow_id text,migration_state text)");
        jdbc.execute("create table binding_authorization_envelopes(tenant_id text,envelope_id text,status text,valid_until timestamptz,admitted_binding_ids_json jsonb)");
        jdbc.execute("create table execution_dispatch_intents_v206(tenant_id text,intent_id text,assignment_id text,lease_id text,fencing_token bigint,status text,external_execution_ref text,send_started_at timestamptz,delivery_unknown_since timestamptz,last_error_code text,last_error_message text,claimed_by text,claim_token text,claim_until timestamptz,updated_at timestamptz)");
        jdbc.execute("create table execution_dispatch_intent_events_v206(tenant_id text,event_id text,intent_id text,assignment_id text,from_status text,to_status text,reason_code text,actor_ref text,evidence_json jsonb,occurred_at timestamptz)");
    }

    private void seed() {
        jdbc.update("insert into dispatch_requests values(?,?,?,?,?,?,?,?,?,?)", "tenant-a", "dispatch-1", "compat-assignment-1", "A0-R7-V206", "canonical-assignment-1", "A0_R7_CANONICAL", "worker-a", "claim-token-1", claimUntil, "DISPATCHING");
        jdbc.update("insert into task_assignments values(?,?,?,?,?)", "tenant-a", "compat-assignment-1", "A0-R7-V206", "7", "canonical-assignment-1");
        jdbc.update("insert into execution_assignments_v206 values(?,?,?,?,?,?,?,?,?,?)", "tenant-a", "canonical-assignment-1", "flow-1", "envelope-1", "binding-1", "LOCAL_FENCED", "lease-1", 7L, now.plusMinutes(10), "ASSIGNED");
        jdbc.update("insert into execution_leases_v206 values(?,?,?,?,?)", "tenant-a", "lease-1", "canonical-assignment-1", "ACTIVE", now.plusMinutes(10));
        jdbc.update("insert into flow_routing_migration_state values(?,?,?)", "tenant-a", "flow-1", "NEW_AUTHORITATIVE");
        jdbc.update("insert into binding_authorization_envelopes values(?,?,?,?,cast(? as jsonb))", "tenant-a", "envelope-1", "ACTIVE", now.plusMinutes(10), "[\"binding-1\"]");
        jdbc.update("insert into execution_dispatch_intents_v206(tenant_id,intent_id,assignment_id,lease_id,fencing_token,status,external_execution_ref,updated_at) values(?,?,?,?,?,?,?,?)", "tenant-a", "intent-1", "canonical-assignment-1", "lease-1", 7L, "HANDED_OFF", "dispatch:dispatch-1", now);
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
