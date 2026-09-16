package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL proof for C0-C2 lease renewal, crash takeover, PUSH handoff and terminal convergence. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0C2RemoteLifecycleConvergenceContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0c2")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private A2ARemoteTaskTrackingService tracking;
    private A2ARemoteAuthorityService authority;

    @BeforeEach
    void setUp() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        named = new NamedParameterJdbcTemplate(ds);
        tracking = new A2ARemoteTaskTrackingService(named, "");
        authority = new A2ARemoteAuthorityService(named, "node-a");
        recreate();
        seedLease("node-a", "token-a", 7L, "STREAM:track-a:7", OffsetDateTime.now().plusMinutes(5));
    }

    @Test
    void liveOwnerCanRenewWithoutChangingEpochOrStream() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease before = tracking.findByTrackingId("tenant-a", "track-a");
        A2ARemoteTaskTrackingService.RemoteTrackingLease after = tracking.renewLease("tenant-a", before);
        assertThat(after).isNotNull();
        assertThat(after.authorityEpoch()).isEqualTo(7L);
        assertThat(after.authoritativeStreamId()).isEqualTo("STREAM:track-a:7");
        assertThat(jdbc.queryForObject("select lease_renewal_count from a2a_remote_tracking_leases where tracking_id='track-a'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void expiredLeaseCannotBeRevivedByOldOwner() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease stale = tracking.findByTrackingId("tenant-a", "track-a");
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second' where tracking_id='track-a'");
        assertThat(tracking.renewLease("tenant-a", stale)).isNull();
    }

    @Test
    void crashTakeoverAdvancesEpochAndFencesOldOwnerRenewal() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease old = tracking.findByTrackingId("tenant-a", "track-a");
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second',next_poll_at=now()-interval '1 second' where tracking_id='track-a'");
        List<A2ARemoteTaskTrackingService.RemoteTrackingLease> claimed = tracking.claimDue("tenant-a", "node-b", 1);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).authorityEpoch()).isEqualTo(8L);
        assertThat(claimed.get(0).ownerInstanceId()).isEqualTo("node-b");
        assertThat(tracking.renewLease("tenant-a", old)).isNull();
    }

    @Test
    void terminalWinningFirstRejectsLaterCancelAndDuplicateTerminal() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease lease = tracking.findByTrackingId("tenant-a", "track-a");
        A2ARemoteTaskTrackingService.TerminalDecision first = tracking.terminalize(
                "tenant-a", lease, "TASK_STATE_COMPLETED", "SUCCEEDED", "STREAM", "evt-terminal-1");
        assertThat(first.won()).isTrue();
        assertThat(first.lifecycleVersion()).isEqualTo(1L);
        assertThat(tracking.requestCancel("tenant-a", "exec-a")).isFalse();
        A2ARemoteTaskTrackingService.TerminalDecision duplicate = tracking.terminalize(
                "tenant-a", lease, "TASK_STATE_COMPLETED", "SUCCEEDED", "STREAM", "evt-terminal-2");
        assertThat(duplicate.won()).isFalse();
        assertThat(jdbc.queryForObject("select terminal_outcome from a2a_remote_tracking_leases where tracking_id='track-a'", String.class))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void cancelWinningFirstInvalidatesOldTerminalAuthority() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease old = tracking.findByTrackingId("tenant-a", "track-a");
        assertThat(tracking.requestCancel("tenant-a", "exec-a")).isTrue();
        A2ARemoteTaskTrackingService.TerminalDecision late = tracking.terminalize(
                "tenant-a", old, "TASK_STATE_COMPLETED", "SUCCEEDED", "STREAM", "evt-late");
        assertThat(late.won()).isFalse();
        Map<String, Object> row = jdbc.queryForMap("select status,authority_epoch,lifecycle_version,terminal_outcome from a2a_remote_tracking_leases where tracking_id='track-a'");
        assertThat(row.get("status")).isEqualTo("CANCELING");
        assertThat(((Number) row.get("authority_epoch")).longValue()).isEqualTo(8L);
        assertThat(((Number) row.get("lifecycle_version")).longValue()).isEqualTo(1L);
        assertThat(row.get("terminal_outcome")).isNull();
    }

    @Test
    void pushIngressPersistsHandoffAndCurrentOwnerClaimsIt() {
        jdbc.update("update a2a_remote_tracking_leases set tracking_mode='PUSH',authoritative_stream_id='PUSH:track-a:7',push_token_hash=? where tracking_id='track-a'", sha256("bearer-a"));
        A2APushInboxService inbox = new A2APushInboxService(named, new ObjectMapper(), null, tracking, authority);
        assertThat(inbox.accept(new A2APushCallbackRouteService.Route("tenant-a", "track-a", "callback-a", "route-fingerprint-a"), "delivery-1", "remote-event-1", Map.of("task", Map.of("id", "remote-a"))))
                .isTrue();
        assertThat(jdbc.queryForObject("select processing_status from a2a_push_inbox where delivery_identity='delivery-1'", String.class))
                .isEqualTo("HANDOFF_PENDING");
        List<A2APushInboxService.PushHandoff> claims = inbox.claimForOwner("tenant-a", "node-a", 10);
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).authorityEpochAtClaim()).isEqualTo(7L);
    }

    @Test
    void takeoverOwnerMayClaimPendingPushHandoff() {
        jdbc.update("update a2a_remote_tracking_leases set tracking_mode='PUSH',authoritative_stream_id='PUSH:track-a:7',push_token_hash=? where tracking_id='track-a'", sha256("bearer-a"));
        A2APushInboxService inbox = new A2APushInboxService(named, new ObjectMapper(), null, tracking, authority);
        assertThat(inbox.accept(new A2APushCallbackRouteService.Route("tenant-a", "track-a", "callback-b", "route-fingerprint-b"), "delivery-2", "remote-event-2", Map.of("task", Map.of("id", "remote-a"))))
                .isTrue();
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second',next_poll_at=now()-interval '1 second' where tracking_id='track-a'");
        List<A2ARemoteTaskTrackingService.RemoteTrackingLease> takeover = tracking.claimDue("tenant-a", "node-b", 1);
        assertThat(takeover).hasSize(1);
        List<A2APushInboxService.PushHandoff> claims = inbox.claimForOwner("tenant-a", "node-b", 10);
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).authorityEpochAtClaim()).isEqualTo(8L);
    }

    private void recreate() {
        jdbc.execute("drop table if exists a2a_push_inbox,a2a_remote_read_executions,a2a_remote_tracking_leases cascade");
        jdbc.execute("""
                create table a2a_remote_read_executions(
                  tenant_id text not null,execution_id text not null,status text,tracking_status text,remote_state text,
                  cancel_requested_at timestamptz,cancel_completed_at timestamptz,terminal_at timestamptz,completed_at timestamptz,
                  last_reconciled_at timestamptz,updated_at timestamptz,
                  remote_lifecycle_version bigint not null default 0,remote_terminal_outcome text,
                  remote_terminal_authority_epoch bigint,remote_terminal_stream_id text,
                  primary key(tenant_id,execution_id))
                """);
        jdbc.execute("""
                create table a2a_remote_tracking_leases(
                  tenant_id text not null,tracking_id text not null,execution_id text not null,delegation_id text,
                  remote_task_id text not null,peer_id text not null,interface_id text not null,tracking_mode text not null,
                  status text not null,push_config_id text,push_token_hash text,failure_count int not null default 0,
                  owner_instance_id text,lease_token text,lease_until timestamptz,next_poll_at timestamptz,
                  last_observed_state text,last_observed_at timestamptz,last_error text,last_remote_error_code text,
                  last_error_class text,last_error_disposition text,last_error_mapping_source text,last_error_mapping_override_id text,
                  authority_epoch bigint not null default 0,authoritative_stream_id text,authority_changed_at timestamptz,
                  lifecycle_version bigint not null default 0,lease_renewed_at timestamptz,lease_renewal_count bigint not null default 0,
                  cancellation_requested_at timestamptz,terminal_outcome text,terminal_source text,terminal_remote_state text,
                  terminal_authority_epoch bigint,terminal_stream_id text,terminal_journal_event_id text,terminalized_at timestamptz,
                  created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
                  primary key(tenant_id,tracking_id),unique(tenant_id,execution_id))
                """);
        jdbc.execute("""
                create table a2a_push_inbox(
                  tenant_id text not null,inbox_id text not null,tracking_id text not null,remote_task_id text not null,
                  authorization_fingerprint text,delivery_identity text,remote_event_identity text,payload_hash text not null,
                  payload_json jsonb not null,processing_status text not null,received_at timestamptz not null default now(),
                  processed_at timestamptz,error_message text,authority_epoch bigint,stream_id text,authority_classification text,
                  authority_reason text,target_owner_instance_id text,target_authority_epoch bigint,handoff_available_at timestamptz,
                  handoff_attempt_count int not null default 0,claimed_by text,claim_token text,claim_until timestamptz,last_handoff_error text,
                  primary key(tenant_id,inbox_id))
                """);
        jdbc.execute("create unique index uq_c0c2_push_delivery on a2a_push_inbox(tenant_id,tracking_id,delivery_identity) where delivery_identity is not null");
        jdbc.execute("create unique index uq_c0c2_push_payload on a2a_push_inbox(tenant_id,tracking_id,payload_hash)");
        jdbc.update("insert into a2a_remote_read_executions(tenant_id,execution_id,status,tracking_status,updated_at) values('tenant-a','exec-a','WAITING_REMOTE','ACTIVE',now())");
    }

    private void seedLease(String owner, String token, long epoch, String stream, OffsetDateTime leaseUntil) {
        jdbc.update("""
                insert into a2a_remote_tracking_leases(
                  tenant_id,tracking_id,execution_id,delegation_id,remote_task_id,peer_id,interface_id,tracking_mode,status,
                  owner_instance_id,lease_token,lease_until,next_poll_at,authority_epoch,authoritative_stream_id,authority_changed_at)
                values('tenant-a','track-a','exec-a','deleg-a','remote-a','peer-a','if-a','STREAM','ACTIVE',?,?,?,?,?,?,now())
                """, owner, token, leaseUntil, OffsetDateTime.now().minusSeconds(1), epoch, stream);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
