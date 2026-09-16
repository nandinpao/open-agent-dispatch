package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C0-C1/C0-C2 durable remote tracking lease and lifecycle convergence authority.
 *
 * <p>The pre-existing Stage 8 lease table remains the single authority table. C0-C1 adds monotonic
 * authority epochs and exactly one authoritative stream id per current lease. C0-C2 adds fenced
 * lease renewal and exactly-once terminal/cancel convergence on the same tracking row; it does not
 * introduce a second authority table.</p>
 */
@Service
public class A2ARemoteTaskTrackingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final String pushBaseUrl;

    public A2ARemoteTaskTrackingService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${opendispatch.a2a.push-base-url:}") String pushBaseUrl) {
        this.jdbc = jdbc;
        this.pushBaseUrl = pushBaseUrl == null ? "" : pushBaseUrl.trim();
    }

    @Transactional
    public RemoteTrackingLease start(
            String tenant,
            String executionId,
            String delegationId,
            String remoteTaskId,
            String peerId,
            String interfaceId,
            boolean streaming,
            boolean push) {
        bind(tenant);
        String mode = mode(streaming, push && !pushBaseUrl.isBlank());
        String trackingId = "a2a-track-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into a2a_remote_tracking_leases(
                    tenant_id,tracking_id,execution_id,delegation_id,remote_task_id,peer_id,interface_id,
                    tracking_mode,status,next_poll_at,authority_epoch,authoritative_stream_id,authority_changed_at,created_at,updated_at)
                values(:tenant,:tracking,:execution,:delegation,:remoteTask,:peer,:interface,
                    :mode,'PENDING',:next,0,null,:now,:now,:now)
                on conflict(tenant_id,execution_id) do update set
                    remote_task_id=excluded.remote_task_id,
                    tracking_mode=excluded.tracking_mode,
                    status=case when a2a_remote_tracking_leases.status='TERMINAL' then a2a_remote_tracking_leases.status else 'PENDING' end,
                    next_poll_at=excluded.next_poll_at,
                    owner_instance_id=null,
                    lease_token=null,
                    lease_until=null,
                    authority_epoch=a2a_remote_tracking_leases.authority_epoch+1,
                    authoritative_stream_id=null,
                    authority_changed_at=excluded.authority_changed_at,
                    updated_at=excluded.updated_at
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("tracking", trackingId)
                .addValue("execution", executionId)
                .addValue("delegation", delegationId)
                .addValue("remoteTask", remoteTaskId)
                .addValue("peer", peerId)
                .addValue("interface", interfaceId)
                .addValue("mode", mode)
                .addValue("next", now)
                .addValue("now", now));
        RemoteTrackingLease row = findByExecution(tenant, executionId);
        jdbc.update("""
                update a2a_remote_read_executions
                   set tracking_mode=:mode,tracking_status='PENDING',tracking_id=:tracking,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("execution", executionId)
                .addValue("mode", row.trackingMode())
                .addValue("tracking", row.trackingId())
                .addValue("now", now));
        return row;
    }

    @Transactional
    public List<RemoteTrackingLease> claimDue(String tenant, String ownerInstanceId, int limit) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusSeconds(45);
        String tokenPrefix = "track-lease-" + UUID.randomUUID();
        return jdbc.query("""
                with due as (
                    select tracking_id
                      from a2a_remote_tracking_leases
                     where tenant_id=:tenant
                       and status in ('PENDING','ACTIVE','RECONCILING','CANCELING')
                       and (lease_until is null or lease_until<:now)
                       and (next_poll_at is null or next_poll_at<=:now)
                     order by coalesce(next_poll_at,updated_at),tracking_id
                     for update skip locked
                     limit :limit
                )
                update a2a_remote_tracking_leases t
                   set owner_instance_id=:owner,
                       lease_token=:tokenPrefix||':'||t.tracking_id||':'||(t.authority_epoch+1)::text,
                       lease_until=:until,
                       authority_epoch=t.authority_epoch+1,
                       authoritative_stream_id=(case
                           when t.status='CANCELING' then 'CANCEL'
                           when t.tracking_mode='POLL' then 'POLL'
                           when t.tracking_mode='PUSH' then 'PUSH'
                           else 'STREAM'
                       end)||':'||t.tracking_id||':'||(t.authority_epoch+1)::text,
                       authority_changed_at=:now,
                       status=case when t.status='PENDING' then 'ACTIVE' else t.status end,
                       updated_at=:now
                  from due
                 where t.tenant_id=:tenant and t.tracking_id=due.tracking_id
                returning t.tracking_id,t.execution_id,t.delegation_id,t.remote_task_id,t.peer_id,t.interface_id,
                          t.tracking_mode,t.status,t.push_config_id,t.failure_count,t.owner_instance_id,t.lease_token,
                          t.lease_until,t.authority_epoch,t.authoritative_stream_id
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("owner", ownerInstanceId)
                .addValue("tokenPrefix", tokenPrefix)
                .addValue("until", until)
                .addValue("now", now)
                .addValue("limit", Math.max(1, Math.min(limit, 50))),
                (rs, n) -> map(rs));
    }

    /**
     * Claims a free PUSH authority lease. If another instance currently owns a valid lease, null is
     * returned and the callback must be journaled as observation/evidence only.
     */
    @Transactional
    public RemoteTrackingLease claimPush(String tenant, String trackingId, String ownerInstanceId) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusSeconds(45);
        String token = "push-lease-" + UUID.randomUUID();
        List<RemoteTrackingLease> rows = jdbc.query("""
                update a2a_remote_tracking_leases t
                   set owner_instance_id=:owner,
                       lease_token=:token,
                       lease_until=:until,
                       authority_epoch=t.authority_epoch+1,
                       authoritative_stream_id='PUSH:'||t.tracking_id||':'||(t.authority_epoch+1)::text,
                       authority_changed_at=:now,
                       status=case when t.status='PENDING' then 'ACTIVE' else t.status end,
                       updated_at=:now
                 where t.tenant_id=:tenant
                   and t.tracking_id=:tracking
                   and t.status in ('PENDING','ACTIVE','RECONCILING')
                   and t.tracking_mode in ('PUSH','HYBRID')
                   and (t.lease_until is null or t.lease_until<:now)
                returning t.tracking_id,t.execution_id,t.delegation_id,t.remote_task_id,t.peer_id,t.interface_id,
                          t.tracking_mode,t.status,t.push_config_id,t.failure_count,t.owner_instance_id,t.lease_token,
                          t.lease_until,t.authority_epoch,t.authoritative_stream_id
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("tracking", trackingId)
                .addValue("owner", ownerInstanceId)
                .addValue("token", token)
                .addValue("until", until)
                .addValue("now", now),
                (rs, n) -> map(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * C0-C2 fenced lease renewal. Renewal preserves owner/token/epoch/stream and can never revive
     * an already expired lease or a lease superseded by takeover/failover.
     */
    @Transactional
    public RemoteTrackingLease renewLease(String tenant, RemoteTrackingLease lease) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusSeconds(45);
        List<RemoteTrackingLease> rows = jdbc.query("""
                update a2a_remote_tracking_leases t
                   set lease_until=:until,
                       lease_renewed_at=:now,
                       lease_renewal_count=t.lease_renewal_count+1,
                       updated_at=:now
                 where t.tenant_id=:tenant
                   and t.tracking_id=:tracking
                   and t.owner_instance_id=:owner
                   and t.lease_token=:token
                   and t.authority_epoch=:epoch
                   and t.authoritative_stream_id=:stream
                   and t.lease_until>:now
                   and t.status in ('ACTIVE','RECONCILING','CANCELING')
                   and t.terminal_outcome is null
                returning t.tracking_id,t.execution_id,t.delegation_id,t.remote_task_id,t.peer_id,t.interface_id,
                          t.tracking_mode,t.status,t.push_config_id,t.failure_count,t.owner_instance_id,t.lease_token,
                          t.lease_until,t.authority_epoch,t.authoritative_stream_id
                """, authorityParams(tenant, lease)
                .addValue("stream", lease.authoritativeStreamId())
                .addValue("until", until)
                .addValue("now", now),
                (rs, n) -> map(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Explicit transport failover: same owner/token, new authority epoch, exactly one new stream. */
    @Transactional
    public RemoteTrackingLease switchAuthority(String tenant, RemoteTrackingLease lease, String source) {
        bind(tenant);
        String normalized = normalizeSource(source);
        OffsetDateTime now = OffsetDateTime.now();
        List<RemoteTrackingLease> rows = jdbc.query("""
                update a2a_remote_tracking_leases t
                   set authority_epoch=t.authority_epoch+1,
                       authoritative_stream_id=:source||':'||t.tracking_id||':'||(t.authority_epoch+1)::text,
                       authority_changed_at=:now,
                       updated_at=:now
                 where t.tenant_id=:tenant
                   and t.tracking_id=:tracking
                   and t.owner_instance_id=:owner
                   and t.lease_token=:token
                   and t.authority_epoch=:epoch
                   and t.lease_until>:now
                   and t.status in ('ACTIVE','RECONCILING','CANCELING')
                   and t.terminal_outcome is null
                returning t.tracking_id,t.execution_id,t.delegation_id,t.remote_task_id,t.peer_id,t.interface_id,
                          t.tracking_mode,t.status,t.push_config_id,t.failure_count,t.owner_instance_id,t.lease_token,
                          t.lease_until,t.authority_epoch,t.authoritative_stream_id
                """, authorityParams(tenant, lease)
                .addValue("source", normalized)
                .addValue("now", now),
                (rs, n) -> map(rs));
        if (rows.isEmpty()) {
            throw new IllegalStateException("A2A_REMOTE_TRACKING_AUTHORITY_LOST");
        }
        return rows.get(0);
    }

    /**
     * Applies one non-terminal observation under the current remote authority fence.
     *
     * <p>PC-S3 cancellation convergence deliberately distinguishes a successful CANCEL transport
     * observation from subsequent reconciliation. A non-terminal CANCEL response means the cancel
     * request was delivered but not yet terminal, so the tracker moves to RECONCILING rather than
     * blindly sending CANCEL again. A later authoritative POLL/STREAM/PUSH observation that proves
     * the remote task is still non-terminal restores CANCELING so the cancellation intent is not
     * silently lost. Ordinary observations without a pending cancellation converge to ACTIVE.</p>
     */
    @Transactional
    public boolean observed(
            String tenant,
            RemoteTrackingLease lease,
            String source,
            String state,
            boolean terminal,
            boolean releaseLease) {
        bind(tenant);
        String normalizedSource = normalizeSource(source);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime next = terminal ? null : nextObservationAt(tenant, lease.trackingId(), now);
        List<String> statuses = jdbc.query("""
                update a2a_remote_tracking_leases
                   set status=case
                           when :terminal then 'TERMINAL'
                           when cancellation_requested_at is not null and :source='CANCEL' then 'RECONCILING'
                           when cancellation_requested_at is not null then 'CANCELING'
                           else 'ACTIVE'
                       end,
                       last_observed_state=:state,
                       last_observed_at=:now,
                       next_poll_at=:next,
                       owner_instance_id=case when :release then null else owner_instance_id end,
                       lease_token=case when :release then null else lease_token end,
                       lease_until=case when :release then null else lease_until end,
                       failure_count=0,
                       last_error=null,
                       last_remote_error_code=null,
                       last_error_class=null,
                       last_error_disposition=null,
                       last_error_mapping_source=null,
                       last_error_mapping_override_id=null,
                       updated_at=:now
                 where tenant_id=:tenant
                   and tracking_id=:tracking
                   and owner_instance_id=:owner
                   and lease_token=:token
                   and authority_epoch=:epoch
                   and authoritative_stream_id=:stream
                   and lease_until>:now
                   and terminal_outcome is null
                returning status
                """, authorityParams(tenant, lease)
                .addValue("source", normalizedSource)
                .addValue("stream", lease.authoritativeStreamId())
                .addValue("terminal", terminal)
                .addValue("state", state)
                .addValue("next", next)
                .addValue("release", terminal || releaseLease)
                .addValue("now", now),
                (rs, n) -> rs.getString("status"));
        if (statuses.size() != 1) {
            return false;
        }
        String effectiveStatus = statuses.get(0);
        jdbc.update("""
                update a2a_remote_read_executions
                   set remote_state=:state,
                       tracking_status=:status,
                       last_remote_event_at=:now,
                       terminal_at=case when :terminal then :now else terminal_at end,
                       updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and remote_terminal_outcome is null
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("tracking", lease.trackingId())
                .addValue("state", state)
                .addValue("status", effectiveStatus)
                .addValue("terminal", terminal)
                .addValue("now", now));
        return true;
    }

    @Transactional
    public boolean releaseIfOwned(String tenant, RemoteTrackingLease lease) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        int changed = jdbc.update("""
                update a2a_remote_tracking_leases
                   set owner_instance_id=null,lease_token=null,lease_until=null,
                       next_poll_at=coalesce(next_poll_at,:next),updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch
                """, authorityParams(tenant, lease)
                .addValue("next", nextObservationAt(tenant, lease.trackingId(), now))
                .addValue("now", now));
        return changed == 1;
    }

    @Transactional
    public void retry(String tenant, RemoteTrackingLease lease, String error, int failures) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        int changed = jdbc.update("""
                update a2a_remote_tracking_leases
                   set status='RECONCILING',failure_count=:failures,last_error=:error,next_poll_at=:next,
                       owner_instance_id=null,lease_token=null,lease_until=null,updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch and lease_until>:now
                   and terminal_outcome is null
                """, authorityParams(tenant, lease)
                .addValue("failures", failures)
                .addValue("error", error)
                .addValue("next", now.plusSeconds(Math.min(120, 5L * Math.max(1, failures))))
                .addValue("now", now));
        if (changed == 1) {
            jdbc.update("update a2a_remote_read_executions set tracking_status='RECONCILING',updated_at=:now where tenant_id=:tenant and execution_id=:execution",
                    new MapSqlParameterSource("tenant", tenant).addValue("execution", lease.executionId()).addValue("now", now));
        }
    }

    @Transactional
    public void mappedRetry(String tenant, RemoteTrackingLease lease, A2APeerErrorMappingService.Resolution resolution) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        int failures = lease.failureCount() + 1;
        MapSqlParameterSource p = errorParams(tenant, lease, resolution)
                .addValue("failures", failures)
                .addValue("next", now.plusSeconds(Math.min(120, 5L * Math.max(1, failures))))
                .addValue("now", now);
        int changed = jdbc.update("""
                update a2a_remote_tracking_leases
                   set status='RECONCILING',failure_count=:failures,last_error=:message,next_poll_at=:next,
                       last_remote_error_code=:remoteCode,last_error_class=:errorClass,last_error_disposition=:disposition,
                       last_error_mapping_source=:mappingSource,last_error_mapping_override_id=:override,
                       owner_instance_id=null,lease_token=null,lease_until=null,updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch and lease_until>:now
                   and terminal_outcome is null
                """, p);
        if (changed == 1) {
            jdbc.update("""
                    update a2a_remote_read_executions
                       set error_code=:code,error_message=:message,remote_error_code=:remoteCode,remote_error_message=:message,
                           canonical_error_class=:errorClass,error_disposition=:disposition,error_mapping_source=:mappingSource,
                           error_mapping_override_id=:override,tracking_status='RECONCILING',updated_at=:now
                     where tenant_id=:tenant and execution_id=:execution
                    """, p);
        }
    }

    @Transactional
    public boolean mappedFailure(String tenant, RemoteTrackingLease lease, A2APeerErrorMappingService.Resolution resolution) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        String executionStatus = resolution.blocking() ? "BLOCKED" : "FAILED";
        String terminalOutcome = resolution.blocking() ? "BLOCKED" : "FAILED";
        String terminalSource = A2ARemoteAuthorityService.sourceOf(lease.authoritativeStreamId());
        MapSqlParameterSource p = errorParams(tenant, lease, resolution)
                .addValue("now", now)
                .addValue("executionStatus", executionStatus)
                .addValue("terminalOutcome", terminalOutcome)
                .addValue("terminalSource", terminalSource)
                .addValue("terminalStream", lease.authoritativeStreamId());
        List<Long> versions = jdbc.query("""
                update a2a_remote_tracking_leases
                   set status='FAILED',failure_count=failure_count+1,last_error=:message,next_poll_at=null,
                       last_remote_error_code=:remoteCode,last_error_class=:errorClass,last_error_disposition=:disposition,
                       last_error_mapping_source=:mappingSource,last_error_mapping_override_id=:override,
                       owner_instance_id=null,lease_token=null,lease_until=null,
                       lifecycle_version=lifecycle_version+1,terminal_outcome=:terminalOutcome,
                       terminal_source=:terminalSource,terminal_remote_state=null,terminal_authority_epoch=:epoch,
                       terminal_stream_id=:terminalStream,terminalized_at=:now,updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch and lease_until>:now
                   and authoritative_stream_id=:terminalStream and terminal_outcome is null
                returning lifecycle_version
                """, p, (rs, n) -> rs.getLong("lifecycle_version"));
        if (!versions.isEmpty()) {
            jdbc.update("""
                    update a2a_remote_read_executions
                       set status=:executionStatus,tracking_status='FAILED',error_code=:code,error_message=:message,
                           remote_error_code=:remoteCode,remote_error_message=:message,canonical_error_class=:errorClass,
                           error_disposition=:disposition,error_mapping_source=:mappingSource,error_mapping_override_id=:override,
                           completed_at=:now,terminal_at=:now,remote_lifecycle_version=:version,
                           remote_terminal_outcome=:terminalOutcome,remote_terminal_authority_epoch=:epoch,
                           remote_terminal_stream_id=:terminalStream,updated_at=:now
                     where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome is null
                    """, p.addValue("version", versions.get(0)));
            return true;
        }
        return false;
    }

    /**
     * C0-C2 exactly-once terminal CAS for authoritative remote terminal events. The same
     * owner/token/epoch/stream fence used by C0-C1 must still be current at apply time.
     */
    @Transactional
    public TerminalDecision terminalize(
            String tenant,
            RemoteTrackingLease lease,
            String remoteState,
            String outcome,
            String source,
            String journalEventId) {
        bind(tenant);
        String terminalOutcome = normalizeTerminalOutcome(outcome);
        String terminalSource = normalizeSource(source);
        OffsetDateTime now = OffsetDateTime.now();
        List<TerminalDecision> rows = jdbc.query("""
                update a2a_remote_tracking_leases t
                   set status='TERMINAL',last_observed_state=:state,last_observed_at=:now,next_poll_at=null,
                       owner_instance_id=null,lease_token=null,lease_until=null,
                       lifecycle_version=t.lifecycle_version+1,
                       terminal_outcome=:outcome,terminal_source=:source,terminal_remote_state=:state,
                       terminal_authority_epoch=:epoch,terminal_stream_id=:stream,terminal_journal_event_id=:journal,
                       terminalized_at=:now,updated_at=:now
                 where t.tenant_id=:tenant and t.tracking_id=:tracking
                   and t.owner_instance_id=:owner and t.lease_token=:token and t.authority_epoch=:epoch
                   and t.authoritative_stream_id=:stream and t.lease_until>:now
                   and t.status in ('ACTIVE','RECONCILING','CANCELING')
                   and t.terminal_outcome is null
                returning t.lifecycle_version,t.terminal_outcome,t.terminal_authority_epoch,t.terminal_stream_id
                """, authorityParams(tenant, lease)
                .addValue("state", remoteState)
                .addValue("outcome", terminalOutcome)
                .addValue("source", terminalSource)
                .addValue("stream", lease.authoritativeStreamId())
                .addValue("journal", journalEventId)
                .addValue("now", now),
                (rs, n) -> new TerminalDecision(true, rs.getLong("lifecycle_version"), rs.getString("terminal_outcome"),
                        rs.getLong("terminal_authority_epoch"), rs.getString("terminal_stream_id")));
        if (rows.isEmpty()) {
            return new TerminalDecision(false, -1L, null, lease.authorityEpoch(), lease.authoritativeStreamId());
        }
        TerminalDecision won = rows.get(0);
        jdbc.update("""
                update a2a_remote_read_executions
                   set remote_state=:state,tracking_status='TERMINAL',terminal_at=:now,
                       remote_lifecycle_version=:version,remote_terminal_outcome=:outcome,
                       remote_terminal_authority_epoch=:epoch,remote_terminal_stream_id=:stream,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome is null
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("execution", lease.executionId())
                .addValue("state", remoteState)
                .addValue("version", won.lifecycleVersion())
                .addValue("outcome", won.outcome())
                .addValue("epoch", won.authorityEpoch())
                .addValue("stream", won.streamId())
                .addValue("now", now));
        return won;
    }

    @Transactional
    public TerminalDecision terminalMappedFailure(
            String tenant,
            RemoteTrackingLease lease,
            String remoteState,
            A2APeerErrorMappingService.Resolution resolution,
            String source,
            String journalEventId) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        String outcome = "TASK_STATE_CANCELED".equals(remoteState)
                ? "CANCELED"
                : (resolution.blocking() ? "BLOCKED" : "FAILED");
        String terminalSource = normalizeSource(source);
        MapSqlParameterSource p = errorParams(tenant, lease, resolution)
                .addValue("state", remoteState)
                .addValue("outcome", outcome)
                .addValue("terminalSource", terminalSource)
                .addValue("terminalStream", lease.authoritativeStreamId())
                .addValue("journal", journalEventId)
                .addValue("now", now);
        List<TerminalDecision> rows = jdbc.query("""
                update a2a_remote_tracking_leases
                   set status='TERMINAL',last_observed_state=:state,last_observed_at=:now,next_poll_at=null,
                       last_error=:message,last_remote_error_code=:remoteCode,last_error_class=:errorClass,
                       last_error_disposition=:disposition,last_error_mapping_source=:mappingSource,
                       last_error_mapping_override_id=:override,owner_instance_id=null,lease_token=null,lease_until=null,
                       lifecycle_version=lifecycle_version+1,terminal_outcome=:outcome,terminal_source=:terminalSource,
                       terminal_remote_state=:state,terminal_authority_epoch=:epoch,terminal_stream_id=:terminalStream,
                       terminal_journal_event_id=:journal,terminalized_at=:now,updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch and lease_until>:now
                   and authoritative_stream_id=:terminalStream and terminal_outcome is null
                returning lifecycle_version,terminal_outcome,terminal_authority_epoch,terminal_stream_id
                """, p,
                (rs, n) -> new TerminalDecision(true, rs.getLong("lifecycle_version"), rs.getString("terminal_outcome"),
                        rs.getLong("terminal_authority_epoch"), rs.getString("terminal_stream_id")));
        if (rows.isEmpty()) {
            return new TerminalDecision(false, -1L, null, lease.authorityEpoch(), lease.authoritativeStreamId());
        }
        TerminalDecision won = rows.get(0);
        jdbc.update("""
                update a2a_remote_read_executions
                   set remote_state=:state,tracking_status='TERMINAL',terminal_at=:now,
                       remote_lifecycle_version=:version,remote_terminal_outcome=:outcome,
                       remote_terminal_authority_epoch=:epoch,remote_terminal_stream_id=:terminalStream,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome is null
                """, p.addValue("version", won.lifecycleVersion()));
        return won;
    }

    /**
     * C0-C2 cancellation request CAS. If a terminal decision already won, cancellation is rejected.
     * If cancellation wins first, it invalidates the old epoch/stream before a CANCEL worker claims.
     */
    @Transactional
    public boolean requestCancel(String tenant, String execution) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        int changed = jdbc.update("""
                update a2a_remote_tracking_leases
                   set status='CANCELING',next_poll_at=:now,
                       owner_instance_id=null,lease_token=null,lease_until=null,
                       authority_epoch=authority_epoch+1,authoritative_stream_id=null,authority_changed_at=:now,
                       lifecycle_version=lifecycle_version+1,cancellation_requested_at=:now,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution
                   and status not in ('TERMINAL','FAILED')
                   and terminal_outcome is null
                """, new MapSqlParameterSource("tenant", tenant).addValue("execution", execution).addValue("now", now));
        if (changed == 1) {
            jdbc.update("""
                    update a2a_remote_read_executions
                       set tracking_status='CANCELING',cancel_requested_at=:now,
                           remote_lifecycle_version=remote_lifecycle_version+1,updated_at=:now
                     where tenant_id=:tenant and execution_id=:execution
                       and remote_terminal_outcome is null
                    """, new MapSqlParameterSource("tenant", tenant).addValue("execution", execution).addValue("now", now));
        }
        return changed == 1;
    }

    @Transactional
    public void pushConfigured(String tenant, String tracking, String configId, String tokenHash) {
        bind(tenant);
        jdbc.update("""
                update a2a_remote_tracking_leases
                   set push_config_id=:config,push_token_hash=:hash,updated_at=:now
                 where tenant_id=:tenant and tracking_id=:tracking
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("tracking", tracking)
                .addValue("config", configId)
                .addValue("hash", tokenHash)
                .addValue("now", OffsetDateTime.now()));
    }

    @Transactional
    public void cancelCompleted(String tenant, String execution) {
        bind(tenant);
        jdbc.update("""
                update a2a_remote_read_executions
                   set cancel_completed_at=:now,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution
                   and remote_terminal_outcome='CANCELED'
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("execution", execution)
                .addValue("now", OffsetDateTime.now()));
    }

    /** C0-C2 stale reconciliation cannot update even projection timestamps after authority loss. */
    @Transactional
    public boolean markReconciled(String tenant, RemoteTrackingLease lease) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        Integer current = jdbc.queryForObject("""
                select count(*) from a2a_remote_tracking_leases
                 where tenant_id=:tenant and tracking_id=:tracking
                   and owner_instance_id=:owner and lease_token=:token and authority_epoch=:epoch
                   and authoritative_stream_id=:stream and lease_until>:now
                   and terminal_outcome is null
                """, authorityParams(tenant, lease).addValue("stream", lease.authoritativeStreamId()).addValue("now", now), Integer.class);
        if (current == null || current != 1) {
            return false;
        }
        return jdbc.update("""
                update a2a_remote_read_executions
                   set last_reconciled_at=:now,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome is null
                """, new MapSqlParameterSource("tenant", tenant).addValue("execution", lease.executionId()).addValue("now", now)) == 1;
    }

    @Transactional(readOnly = true)
    public RemoteTrackingLease findByExecution(String tenant, String execution) {
        bind(tenant);
        return jdbc.queryForObject(selectLease() + " where tenant_id=:tenant and execution_id=:execution",
                new MapSqlParameterSource("tenant", tenant).addValue("execution", execution), (rs, n) -> map(rs));
    }

    @Transactional(readOnly = true)
    public RemoteTrackingLease findByTrackingId(String tenant, String trackingId) {
        bind(tenant);
        return jdbc.queryForObject(selectLease() + " where tenant_id=:tenant and tracking_id=:tracking",
                new MapSqlParameterSource("tenant", tenant).addValue("tracking", trackingId), (rs, n) -> map(rs));
    }

    @Transactional(readOnly = true)
    public RemoteTrackingLease findByTrackingIdOrNull(String tenant, String trackingId) {
        try {
            return findByTrackingId(tenant, trackingId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public String pushBaseUrl() {
        return pushBaseUrl;
    }

    private OffsetDateTime nextObservationAt(String tenant, String tracking, OffsetDateTime now) {
        String trackingMode = jdbc.queryForObject(
                "select tracking_mode from a2a_remote_tracking_leases where tenant_id=:tenant and tracking_id=:tracking",
                new MapSqlParameterSource("tenant", tenant).addValue("tracking", tracking), String.class);
        return now.plusSeconds("PUSH".equals(trackingMode) || "HYBRID".equals(trackingMode) ? 60 : 10);
    }

    private String mode(boolean stream, boolean push) {
        if (push && stream) return "HYBRID";
        if (push) return "PUSH";
        if (stream) return "STREAM";
        return "POLL";
    }

    private static String normalizeSource(String source) {
        String value = source == null ? "" : source.trim().toUpperCase();
        return switch (value) {
            case "POLL", "STREAM", "PUSH", "CANCEL", "RECONCILIATION" -> value;
            default -> throw new IllegalArgumentException("A2A_REMOTE_EVENT_SOURCE_INVALID:" + value);
        };
    }

    private MapSqlParameterSource authorityParams(String tenant, RemoteTrackingLease lease) {
        return new MapSqlParameterSource("tenant", tenant)
                .addValue("tracking", lease.trackingId())
                .addValue("execution", lease.executionId())
                .addValue("owner", lease.ownerInstanceId())
                .addValue("token", lease.leaseToken())
                .addValue("epoch", lease.authorityEpoch());
    }

    private MapSqlParameterSource errorParams(String tenant, RemoteTrackingLease lease, A2APeerErrorMappingService.Resolution resolution) {
        return authorityParams(tenant, lease)
                .addValue("code", resolution.canonicalErrorCode())
                .addValue("message", resolution.remoteErrorMessage())
                .addValue("remoteCode", resolution.remoteErrorCode())
                .addValue("errorClass", resolution.resolvedErrorClass())
                .addValue("disposition", resolution.disposition())
                .addValue("mappingSource", resolution.mappingSource())
                .addValue("override", resolution.overrideId());
    }

    private static String selectLease() {
        return """
                select tracking_id,execution_id,delegation_id,remote_task_id,peer_id,interface_id,
                       tracking_mode,status,push_config_id,failure_count,owner_instance_id,lease_token,
                       lease_until,authority_epoch,authoritative_stream_id
                  from a2a_remote_tracking_leases
                """;
    }

    private static RemoteTrackingLease map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RemoteTrackingLease(
                rs.getString("tracking_id"),
                rs.getString("execution_id"),
                rs.getString("delegation_id"),
                rs.getString("remote_task_id"),
                rs.getString("peer_id"),
                rs.getString("interface_id"),
                rs.getString("tracking_mode"),
                rs.getString("status"),
                rs.getString("push_config_id"),
                rs.getInt("failure_count"),
                rs.getString("owner_instance_id"),
                rs.getString("lease_token"),
                rs.getObject("lease_until", OffsetDateTime.class),
                rs.getLong("authority_epoch"),
                rs.getString("authoritative_stream_id"));
    }

    private static String normalizeTerminalOutcome(String outcome) {
        String value = outcome == null ? "" : outcome.trim().toUpperCase();
        return switch (value) {
            case "SUCCEEDED", "FAILED", "BLOCKED", "CANCELED" -> value;
            default -> throw new IllegalArgumentException("A2A_REMOTE_TERMINAL_OUTCOME_INVALID:" + value);
        };
    }

    public record TerminalDecision(boolean won, long lifecycleVersion, String outcome, long authorityEpoch, String streamId) {}

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "a2a-remote-tracking");
    }

    public record RemoteTrackingLease(
            String trackingId,
            String executionId,
            String delegationId,
            String remoteTaskId,
            String peerId,
            String interfaceId,
            String trackingMode,
            String status,
            String pushConfigId,
            int failureCount,
            String ownerInstanceId,
            String leaseToken,
            OffsetDateTime leaseUntil,
            long authorityEpoch,
            String authoritativeStreamId) {
        public String mode() {
            return trackingMode;
        }
    }
}
