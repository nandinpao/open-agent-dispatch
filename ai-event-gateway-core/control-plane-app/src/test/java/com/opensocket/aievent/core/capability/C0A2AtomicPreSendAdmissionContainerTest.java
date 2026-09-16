package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchPreSendAdmissionDecision;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.NettyDispatchCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** PostgreSQL proof for C0-A2's lock/revalidate/CAS pre-send authority boundary. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0A2AtomicPreSendAdmissionContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0a2")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private A0R7AtomicPreSendAdmission admission;
    private OffsetDateTime now;
    private OffsetDateTime claimUntil;

    @BeforeEach
    void setUp() {
        dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
        admission = new A0R7AtomicPreSendAdmission(
                new NamedParameterJdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource));
        now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        claimUntil = now.plusMinutes(5);
        recreateSchema();
        seedAuthority();
    }

    @Test
    void admissionMustAtomicallyCommitSendStartedAndReturnImmutablePermit() {
        DispatchPreSendAdmissionDecision decision = admission.admit(request(), now);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.applicable()).isTrue();
        assertThat(decision.permit()).isNotNull();
        assertThat(decision.permit().authorityVersion()).isEqualTo("A0-R7-V206");
        assertThat(decision.permit().assignmentId()).isEqualTo("canonical-assignment-1");
        assertThat(decision.permit().leaseId()).isEqualTo("lease-1");
        assertThat(decision.permit().fencingToken()).isEqualTo(7L);
        assertThat(decision.permit().externalExecutionRef()).isEqualTo("dispatch:dispatch-1");

        assertThat(jdbc.queryForObject(
                "select status from execution_dispatch_intents_v206 where tenant_id='tenant-a' and intent_id='intent-1'",
                String.class)).isEqualTo("SEND_STARTED");
        assertThat(jdbc.queryForObject(
                "select count(*) from execution_dispatch_intent_events_v206 where reason_code='C0_A2_ATOMIC_PRE_SEND_ADMITTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentWorkersMustNotBothAcquireSendPermitForSameIntent() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<DispatchPreSendAdmissionDecision>> futures = List.of(
                    executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return admission.admit(request(), now); }),
                    executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return admission.admit(request(), now); }));
            start.countDown();
            List<DispatchPreSendAdmissionDecision> results = futures.stream().map(this::get).toList();

            assertThat(results.stream().filter(DispatchPreSendAdmissionDecision::allowed).count()).isEqualTo(1);
            assertThat(results.stream().filter(r -> !r.allowed()).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from execution_dispatch_intent_events_v206 where reason_code='C0_A2_ATOMIC_PRE_SEND_ADMITTED'",
                    Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void staleDispatchClaimMustFailBeforeSendStarted() {
        jdbc.update("update dispatch_requests set claim_until=? where dispatch_request_id='dispatch-1'", now.minusSeconds(1));

        DispatchPreSendAdmissionDecision decision = admission.admit(request(), now);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("A0_R7_DISPATCH_CLAIM_EXPIRED");
        assertThat(jdbc.queryForObject(
                "select status from execution_dispatch_intents_v206 where tenant_id='tenant-a' and intent_id='intent-1'",
                String.class)).isEqualTo("HANDED_OFF");
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
        jdbc.execute("create table execution_dispatch_intents_v206(tenant_id text,intent_id text,assignment_id text,lease_id text,fencing_token bigint,status text,external_execution_ref text,send_started_at timestamptz,updated_at timestamptz)");
        jdbc.execute("create table execution_dispatch_intent_events_v206(tenant_id text,event_id text,intent_id text,assignment_id text,from_status text,to_status text,reason_code text,actor_ref text,evidence_json jsonb,occurred_at timestamptz)");
    }

    private void seedAuthority() {
        jdbc.update("insert into dispatch_requests values(?,?,?,?,?,?,?,?,?,?)", "tenant-a", "dispatch-1", "compat-assignment-1", "A0-R7-V206", "canonical-assignment-1", "A0_R7_CANONICAL", "worker-a", "claim-token-1", claimUntil, "DISPATCHING");
        jdbc.update("insert into task_assignments values(?,?,?,?,?)", "tenant-a", "compat-assignment-1", "A0-R7-V206", "7", "canonical-assignment-1");
        jdbc.update("insert into execution_assignments_v206 values(?,?,?,?,?,?,?,?,?,?)", "tenant-a", "canonical-assignment-1", "flow-1", "envelope-1", "binding-1", "LOCAL_FENCED", "lease-1", 7L, now.plusMinutes(10), "ASSIGNED");
        jdbc.update("insert into execution_leases_v206 values(?,?,?,?,?)", "tenant-a", "lease-1", "canonical-assignment-1", "ACTIVE", now.plusMinutes(10));
        jdbc.update("insert into flow_routing_migration_state values(?,?,?)", "tenant-a", "flow-1", "NEW_AUTHORITATIVE");
        jdbc.update("insert into binding_authorization_envelopes values(?,?,?,?,cast(? as jsonb))", "tenant-a", "envelope-1", "ACTIVE", now.plusMinutes(10), "[\"binding-1\"]");
        jdbc.update("insert into execution_dispatch_intents_v206 values(?,?,?,?,?,?,?,?,?)", "tenant-a", "intent-1", "canonical-assignment-1", "lease-1", 7L, "HANDED_OFF", "dispatch:dispatch-1", null, now);
    }

    private DispatchPreSendAdmissionDecision get(Future<DispatchPreSendAdmissionDecision> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
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
