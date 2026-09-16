package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-C2 durable PUSH ingress and owner-independent handoff queue.
 *
 * <p>Authentication admits the callback into durable storage. The HTTP recipient instance does not
 * become lifecycle authority. A current RemoteTrackingLease owner later claims the inbox row and
 * presents the C0-C1 owner/token/expiry/epoch/stream fence to observation processing.</p>
 */
@Service
public class A2APushInboxService {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final A2ARemoteTaskObservationService observation;
    private final A2ARemoteTaskTrackingService tracking;
    private final A2ARemoteAuthorityService authority;

    public A2APushInboxService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            A2ARemoteTaskObservationService observation,
            A2ARemoteTaskTrackingService tracking,
            A2ARemoteAuthorityService authority) {
        this.jdbc = jdbc;
        this.json = json;
        this.observation = observation;
        this.tracking = tracking;
        this.authority = authority;
    }

    /** Durable admission only. The opaque callback route was authenticated before tenant binding. */
    @Transactional
    public boolean accept(
            A2APushCallbackRouteService.Route route,
            String deliveryIdentity,
            String remoteEventIdentity,
            Map<String, Object> payload) {
        if (route == null) throw new IllegalArgumentException("A2A_PUSH_AUTHENTICATED_ROUTE_REQUIRED");
        String tenant = route.tenantId();
        String trackingId = route.trackingId();
        bind(tenant);
        A2ARemoteTaskTrackingService.RemoteTrackingLease current = tracking.findByTrackingId(tenant, trackingId);
        String raw = write(payload == null ? Map.of() : payload);
        String hash = sha256(raw);
        String inboxId = "a2a-push-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String targetOwner = liveOwner(current, now) ? current.ownerInstanceId() : null;
        Long targetEpoch = liveOwner(current, now) ? current.authorityEpoch() : null;
        try {
            jdbc.update("""
                    insert into a2a_push_inbox(
                        tenant_id,inbox_id,tracking_id,remote_task_id,authorization_fingerprint,delivery_identity,
                        remote_event_identity,payload_hash,payload_json,processing_status,received_at,
                        target_owner_instance_id,target_authority_epoch,handoff_available_at)
                    values(:tenant,:inbox,:tracking,:remoteTask,:auth,:delivery,
                        :remoteEvent,:hash,cast(:payload as jsonb),'HANDOFF_PENDING',:now,
                        :targetOwner,:targetEpoch,:now)
                    """, new MapSqlParameterSource("tenant", tenant)
                    .addValue("inbox", inboxId)
                    .addValue("tracking", trackingId)
                    .addValue("remoteTask", current.remoteTaskId())
                    .addValue("auth", route.authorizationFingerprint())
                    .addValue("delivery", blankToNull(deliveryIdentity))
                    .addValue("remoteEvent", blankToNull(remoteEventIdentity))
                    .addValue("hash", hash)
                    .addValue("payload", raw)
                    .addValue("targetOwner", targetOwner)
                    .addValue("targetEpoch", targetEpoch)
                    .addValue("now", now));
        } catch (DuplicateKeyException ex) {
            return false;
        }
        return true;
    }

    /**
     * Claims durable PUSH work only when this runtime instance is the current live lease owner.
     * The stored target owner/epoch are hints; a valid crash/takeover owner may claim stale handoff.
     */
    @Transactional
    public List<PushHandoff> claimForOwner(String tenant, String ownerInstanceId, int limit) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime claimUntil = now.plusSeconds(30);
        String tokenPrefix = "push-handoff-" + UUID.randomUUID();
        return jdbc.query("""
                with due as (
                    select i.inbox_id
                      from a2a_push_inbox i
                      join a2a_remote_tracking_leases t
                        on t.tenant_id=i.tenant_id and t.tracking_id=i.tracking_id
                     where i.tenant_id=:tenant
                       and i.processing_status in ('RECEIVED','HANDOFF_PENDING','CLAIMED')
                       and coalesce(i.handoff_available_at,i.received_at)<=:now
                       and (i.processing_status<>'CLAIMED' or i.claim_until is null or i.claim_until<:now)
                       and t.owner_instance_id=:owner
                       and t.lease_until>:now
                       and t.status in ('ACTIVE','RECONCILING','CANCELING')
                       and t.terminal_outcome is null
                     order by i.received_at,i.inbox_id
                     for update of i skip locked
                     limit :limit
                )
                update a2a_push_inbox i
                   set processing_status='CLAIMED',claimed_by=:owner,
                       claim_token=:tokenPrefix||':'||i.inbox_id,
                       claim_until=:claimUntil,
                       target_owner_instance_id=:owner,
                       target_authority_epoch=t.authority_epoch,
                       handoff_attempt_count=i.handoff_attempt_count+1,
                       last_handoff_error=null
                  from due, a2a_remote_tracking_leases t
                 where i.tenant_id=:tenant and i.inbox_id=due.inbox_id
                   and t.tenant_id=i.tenant_id and t.tracking_id=i.tracking_id
                returning i.inbox_id,i.tracking_id,i.remote_event_identity,i.payload_json::text,
                          i.claim_token,i.claim_until,t.authority_epoch,t.authoritative_stream_id
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("owner", ownerInstanceId)
                .addValue("now", now)
                .addValue("claimUntil", claimUntil)
                .addValue("tokenPrefix", tokenPrefix)
                .addValue("limit", Math.max(1, Math.min(limit, 50))),
                (rs, n) -> new PushHandoff(
                        rs.getString("inbox_id"),
                        rs.getString("tracking_id"),
                        rs.getString("remote_event_identity"),
                        readMap(rs.getString("payload_json")),
                        rs.getString("claim_token"),
                        rs.getObject("claim_until", OffsetDateTime.class),
                        rs.getLong("authority_epoch"),
                        rs.getString("authoritative_stream_id")));
    }

    /** Applies one claimed inbox event through the current C0-C1 authority fence. */
    @Transactional
    public boolean processClaimed(String tenant, String ownerInstanceId, PushHandoff handoff) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        Integer claimCurrent = jdbc.queryForObject("""
                select count(*) from a2a_push_inbox
                 where tenant_id=:tenant and inbox_id=:inbox and processing_status='CLAIMED'
                   and claimed_by=:owner and claim_token=:claimToken and claim_until>:now
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("inbox", handoff.inboxId())
                .addValue("owner", ownerInstanceId)
                .addValue("claimToken", handoff.claimToken())
                .addValue("now", now), Integer.class);
        if (claimCurrent == null || claimCurrent != 1) {
            return false;
        }

        A2ARemoteTaskTrackingService.RemoteTrackingLease lease = tracking.findByTrackingIdOrNull(tenant, handoff.trackingId());
        if (lease == null || !ownerInstanceId.equals(lease.ownerInstanceId())
                || lease.leaseUntil() == null || !lease.leaseUntil().isAfter(now)) {
            releaseClaim(tenant, handoff, "CURRENT_LEASE_OWNER_UNAVAILABLE");
            return false;
        }
        A2ARemoteTaskTrackingService.RemoteTrackingLease renewed = tracking.renewLease(tenant, lease);
        if (renewed == null) {
            releaseClaim(tenant, handoff, "LEASE_RENEWAL_LOST_BEFORE_PUSH_APPLY");
            return false;
        }
        lease = renewed;

        A2ARemoteTaskObservationService.ObservationResult result = observation.observe(
                tenant,
                lease,
                "PUSH",
                handoff.payload(),
                blankToNull(handoff.remoteEventIdentity()),
                authority.context(lease, "PUSH"));

        int changed = jdbc.update("""
                update a2a_push_inbox
                   set processing_status='PROCESSED',processed_at=:now,
                       authority_epoch=:epoch,stream_id=:stream,
                       authority_classification=:classification,authority_reason=:reason,
                       claimed_by=null,claim_token=null,claim_until=null,last_handoff_error=null
                 where tenant_id=:tenant and inbox_id=:inbox and processing_status='CLAIMED'
                   and claimed_by=:owner and claim_token=:claimToken
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("inbox", handoff.inboxId())
                .addValue("owner", ownerInstanceId)
                .addValue("claimToken", handoff.claimToken())
                .addValue("epoch", lease.authorityEpoch())
                .addValue("stream", A2ARemoteAuthorityService.streamId("PUSH", lease.trackingId(), lease.authorityEpoch()))
                .addValue("classification", result.authoritative() ? "AUTHORITATIVE" : "OBSERVATION")
                .addValue("reason", result.reason())
                .addValue("now", OffsetDateTime.now()));
        return changed == 1;
    }

    @Transactional
    public void releaseClaim(String tenant, PushHandoff handoff, String error) {
        bind(tenant);
        jdbc.update("""
                update a2a_push_inbox
                   set processing_status='HANDOFF_PENDING',handoff_available_at=:next,
                       claimed_by=null,claim_token=null,claim_until=null,last_handoff_error=:error
                 where tenant_id=:tenant and inbox_id=:inbox and claim_token=:claimToken
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("inbox", handoff.inboxId())
                .addValue("claimToken", handoff.claimToken())
                .addValue("error", error == null ? "PUSH_HANDOFF_RETRY" : error)
                .addValue("next", OffsetDateTime.now().plusSeconds(2)));
    }

    private static boolean liveOwner(A2ARemoteTaskTrackingService.RemoteTrackingLease lease, OffsetDateTime now) {
        return lease.ownerInstanceId() != null && lease.leaseUntil() != null && lease.leaseUntil().isAfter(now);
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "a2a-push-inbox");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : json.readValue(value, OBJECT_MAP);
        } catch (Exception ex) {
            throw new IllegalStateException("A2A_PUSH_HANDOFF_PAYLOAD_INVALID", ex);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PushHandoff(
            String inboxId,
            String trackingId,
            String remoteEventIdentity,
            Map<String, Object> payload,
            String claimToken,
            OffsetDateTime claimUntil,
            long authorityEpochAtClaim,
            String authoritativeStreamIdAtClaim) {}
}
