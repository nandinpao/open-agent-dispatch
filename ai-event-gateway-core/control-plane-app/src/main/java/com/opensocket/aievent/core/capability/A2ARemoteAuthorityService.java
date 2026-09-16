package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C0-C1 single authority evaluator for remote A2A lifecycle events.
 *
 * <p>Transport validity is not lifecycle authority. An event is authoritative only when the
 * presenting instance still owns the current lease, presents the current lease token, the lease is
 * unexpired, the authority epoch matches, and the event stream id equals the lease's single
 * authoritative stream id.</p>
 */
@Service
public class A2ARemoteAuthorityService {
    private final NamedParameterJdbcTemplate jdbc;
    private final String instanceId;

    public A2ARemoteAuthorityService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${opendispatch.a2a-async.instance-id:}") String configuredInstanceId) {
        this.jdbc = jdbc;
        String configured = configuredInstanceId == null ? "" : configuredInstanceId.trim();
        this.instanceId = configured.isBlank()
                ? "a2a-authority-" + UUID.randomUUID().toString().substring(0, 12)
                : configured;
    }

    public String instanceId() {
        return instanceId;
    }

    public EventAuthorityContext context(A2ARemoteTaskTrackingService.RemoteTrackingLease lease, String source) {
        return new EventAuthorityContext(
                lease.ownerInstanceId(),
                lease.leaseToken(),
                lease.authorityEpoch(),
                streamId(source, lease.trackingId(), lease.authorityEpoch()));
    }

    public EventAuthorityContext observationContext(A2ARemoteTaskTrackingService.RemoteTrackingLease lease, String source) {
        return new EventAuthorityContext(
                instanceId,
                "UNOWNED_OBSERVATION",
                lease.authorityEpoch(),
                streamId(source, lease.trackingId(), lease.authorityEpoch()));
    }

    public static String streamId(String source, String trackingId, long authorityEpoch) {
        return normalizedSource(source) + ":" + trackingId + ":" + authorityEpoch;
    }

    public static String sourceOf(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return "";
        }
        int idx = streamId.indexOf(':');
        return idx < 0 ? streamId : streamId.substring(0, idx);
    }

    @Transactional
    public AuthorityDecision evaluate(String tenant, String trackingId, EventAuthorityContext context) {
        bind(tenant);
        List<LeaseAuthorityRow> rows = jdbc.query("""
                select owner_instance_id, lease_token, lease_until, authority_epoch, authoritative_stream_id, status
                  from a2a_remote_tracking_leases
                 where tenant_id=:tenant and tracking_id=:tracking
                 for share
                """, new MapSqlParameterSource("tenant", tenant).addValue("tracking", trackingId),
                (rs, n) -> new LeaseAuthorityRow(
                        rs.getString("owner_instance_id"),
                        rs.getString("lease_token"),
                        rs.getObject("lease_until", OffsetDateTime.class),
                        rs.getLong("authority_epoch"),
                        rs.getString("authoritative_stream_id"),
                        rs.getString("status")));
        if (rows.isEmpty()) {
            return new AuthorityDecision(false, "TRACKING_LEASE_NOT_FOUND", 0L, null);
        }
        LeaseAuthorityRow current = rows.get(0);
        if ("TERMINAL".equals(current.status()) || "FAILED".equals(current.status())) {
            return new AuthorityDecision(false, "TRACKING_NOT_ACTIVE", current.authorityEpoch(), current.authoritativeStreamId());
        }
        if (current.ownerInstanceId() == null || !current.ownerInstanceId().equals(context.ownerInstanceId())) {
            return new AuthorityDecision(false, "OWNER_MISMATCH", current.authorityEpoch(), current.authoritativeStreamId());
        }
        if (current.leaseToken() == null || context.leaseToken() == null
                || !MessageDigest.isEqual(current.leaseToken().getBytes(StandardCharsets.UTF_8), context.leaseToken().getBytes(StandardCharsets.UTF_8))) {
            return new AuthorityDecision(false, "LEASE_TOKEN_MISMATCH", current.authorityEpoch(), current.authoritativeStreamId());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (current.leaseUntil() == null || !current.leaseUntil().isAfter(now)) {
            return new AuthorityDecision(false, "LEASE_EXPIRED", current.authorityEpoch(), current.authoritativeStreamId());
        }
        if (current.authorityEpoch() != context.authorityEpoch()) {
            return new AuthorityDecision(false, "AUTHORITY_EPOCH_STALE", current.authorityEpoch(), current.authoritativeStreamId());
        }
        if (current.authoritativeStreamId() == null || !current.authoritativeStreamId().equals(context.streamId())) {
            return new AuthorityDecision(false, "NON_AUTHORITATIVE_STREAM", current.authorityEpoch(), current.authoritativeStreamId());
        }
        return new AuthorityDecision(true, "CURRENT_AUTHORITY", current.authorityEpoch(), current.authoritativeStreamId());
    }

    public static String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, instanceId);
    }

    private static String normalizedSource(String source) {
        String value = source == null ? "" : source.trim().toUpperCase();
        return switch (value) {
            case "POLL", "STREAM", "PUSH", "CANCEL", "RECONCILIATION" -> value;
            default -> throw new IllegalArgumentException("A2A_REMOTE_EVENT_SOURCE_INVALID:" + value);
        };
    }

    public record EventAuthorityContext(String ownerInstanceId, String leaseToken, long authorityEpoch, String streamId) {}
    public record AuthorityDecision(boolean authoritative, String reason, long currentAuthorityEpoch, String currentAuthoritativeStreamId) {}
    private record LeaseAuthorityRow(String ownerInstanceId, String leaseToken, OffsetDateTime leaseUntil, long authorityEpoch, String authoritativeStreamId, String status) {}
}
