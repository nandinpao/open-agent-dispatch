package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
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

/** PostgreSQL proof for C0-C1 current-owner / epoch / authoritative-stream fencing. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0C1AuthoritativeRemoteStreamContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0c1")
            .withUsername("opendispatch")
            .withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private A2ARemoteAuthorityService authority;
    private A2ARemoteEventJournalService journal;
    private A2ARemoteTaskTrackingService tracking;

    @BeforeEach
    void setUp() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        named = new NamedParameterJdbcTemplate(ds);
        authority = new A2ARemoteAuthorityService(named, "node-a");
        journal = new A2ARemoteEventJournalService(named, new ObjectMapper(), authority);
        tracking = new A2ARemoteTaskTrackingService(named, "");
        recreate();
        seedLease();
    }

    @Test
    void currentOwnerEpochAndStreamIsAuthoritative() {
        A2ARemoteEventJournalService.AppendResult result = journal.append(
                "tenant-a", "track-a", "exec-a", "remote-a", "STREAM", "STATUS_UPDATE", "working", "evt-1",
                Map.of("statusUpdate", Map.of("state", "working")),
                new A2ARemoteAuthorityService.EventAuthorityContext("node-a", "token-a", 7L, "STREAM:track-a:7"));
        assertThat(result.authoritative()).isTrue();
        assertThat(jdbc.queryForMap("select is_authoritative,authority_decision_reason,authority_contract_version from a2a_remote_event_journal where journal_event_id=?", result.journalEventId()))
                .containsEntry("is_authoritative", true)
                .containsEntry("authority_decision_reason", "CURRENT_AUTHORITY")
                .containsEntry("authority_contract_version", "C0_C1_V1");
    }

    @Test
    void staleOwnerIsObservationOnly() {
        A2ARemoteEventJournalService.AppendResult result = append("node-b", "token-a", 7L, "STREAM:track-a:7", "evt-owner");
        assertThat(result.authoritative()).isFalse();
        assertThat(result.authorityReason()).isEqualTo("OWNER_MISMATCH");
    }

    @Test
    void staleEpochIsObservationOnly() {
        A2ARemoteEventJournalService.AppendResult result = append("node-a", "token-a", 6L, "STREAM:track-a:6", "evt-epoch");
        assertThat(result.authoritative()).isFalse();
        assertThat(result.authorityReason()).isEqualTo("AUTHORITY_EPOCH_STALE");
    }

    @Test
    void wrongTransportStreamIsObservationOnly() {
        A2ARemoteEventJournalService.AppendResult result = append("node-a", "token-a", 7L, "POLL:track-a:7", "evt-poll");
        assertThat(result.authoritative()).isFalse();
        assertThat(result.authorityReason()).isEqualTo("NON_AUTHORITATIVE_STREAM");
    }

    @Test
    void expiredLeaseIsObservationOnly() {
        jdbc.update("update a2a_remote_tracking_leases set lease_until=now()-interval '1 second' where tracking_id='track-a'");
        A2ARemoteEventJournalService.AppendResult result = append("node-a", "token-a", 7L, "STREAM:track-a:7", "evt-expired");
        assertThat(result.authoritative()).isFalse();
        assertThat(result.authorityReason()).isEqualTo("LEASE_EXPIRED");
    }

    @Test
    void streamToPollFallbackMustAdvanceAuthorityEpoch() {
        A2ARemoteTaskTrackingService.RemoteTrackingLease lease = tracking.findByTrackingId("tenant-a", "track-a");
        A2ARemoteTaskTrackingService.RemoteTrackingLease poll = tracking.switchAuthority("tenant-a", lease, "POLL");
        assertThat(poll.authorityEpoch()).isEqualTo(8L);
        assertThat(poll.authoritativeStreamId()).isEqualTo("POLL:track-a:8");
        A2ARemoteAuthorityService.AuthorityDecision oldStream = authority.evaluate(
                "tenant-a", "track-a", new A2ARemoteAuthorityService.EventAuthorityContext("node-a", "token-a", 7L, "STREAM:track-a:7"));
        assertThat(oldStream.authoritative()).isFalse();
        assertThat(oldStream.reason()).isEqualTo("AUTHORITY_EPOCH_STALE");
        A2ARemoteAuthorityService.AuthorityDecision newPoll = authority.evaluate(
                "tenant-a", "track-a", authority.context(poll, "POLL"));
        assertThat(newPoll.authoritative()).isTrue();
    }

    private A2ARemoteEventJournalService.AppendResult append(String owner, String token, long epoch, String stream, String eventId) {
        return journal.append(
                "tenant-a", "track-a", "exec-a", "remote-a", "STREAM", "STATUS_UPDATE", "working", eventId,
                Map.of("statusUpdate", Map.of("state", "working")),
                new A2ARemoteAuthorityService.EventAuthorityContext(owner, token, epoch, stream));
    }

    private void recreate() {
        jdbc.execute("drop table if exists a2a_remote_event_journal,a2a_remote_tracking_leases cascade");
        jdbc.execute("""
                create table a2a_remote_tracking_leases(
                  tenant_id text not null,tracking_id text not null,execution_id text not null,delegation_id text,
                  remote_task_id text not null,peer_id text not null,interface_id text not null,tracking_mode text not null,
                  status text not null,push_config_id text,failure_count int not null default 0,owner_instance_id text,
                  lease_token text,lease_until timestamptz,next_poll_at timestamptz,last_observed_state text,last_observed_at timestamptz,
                  last_error text,last_remote_error_code text,last_error_class text,last_error_disposition text,last_error_mapping_source text,
                  last_error_mapping_override_id text,authority_epoch bigint not null default 0,authoritative_stream_id text,
                  authority_changed_at timestamptz,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
                  primary key(tenant_id,tracking_id),unique(tenant_id,execution_id))
                """);
        jdbc.execute("""
                create table a2a_remote_event_journal(
                  tenant_id text not null,journal_event_id text not null,tracking_id text not null,execution_id text not null,
                  remote_task_id text not null,source text not null,event_type text not null,remote_state text,remote_event_id text,
                  dedup_key text,payload_hash text not null,payload_json jsonb not null,local_remote_sequence bigint not null,
                  stream_id text,is_authoritative boolean not null default true,authority_epoch bigint,lease_owner_instance_id text,
                  lease_token_fingerprint text,authority_decision_reason text,authority_contract_version text not null default 'PRE_C0_C1',
                  observed_at timestamptz not null default now(),primary key(tenant_id,journal_event_id))
                """);
        jdbc.execute("create unique index uq_c0c1_remote_event_identity on a2a_remote_event_journal(tenant_id,tracking_id,dedup_key) where dedup_key is not null");
    }

    private void seedLease() {
        jdbc.update("""
                insert into a2a_remote_tracking_leases(
                  tenant_id,tracking_id,execution_id,remote_task_id,peer_id,interface_id,tracking_mode,status,
                  owner_instance_id,lease_token,lease_until,authority_epoch,authoritative_stream_id,authority_changed_at)
                values('tenant-a','track-a','exec-a','remote-a','peer-a','if-a','STREAM','ACTIVE',
                  'node-a','token-a',now()+interval '5 minutes',7,'STREAM:track-a:7',now())
                """);
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
