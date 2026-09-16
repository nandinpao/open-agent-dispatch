package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Append-only remote event evidence with C0-C1 authority classification. */
@Service
public class A2ARemoteEventJournalService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final A2ARemoteAuthorityService authority;

    public A2ARemoteEventJournalService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            A2ARemoteAuthorityService authority) {
        this.jdbc = jdbc;
        this.json = json;
        this.authority = authority;
    }

    @Transactional
    public AppendResult append(
            String tenant,
            String trackingId,
            String executionId,
            String remoteTaskId,
            String source,
            String eventType,
            String remoteState,
            String remoteEventId,
            Map<String, Object> payload,
            A2ARemoteAuthorityService.EventAuthorityContext context) {
        bind(tenant);
        String raw = write(payload == null ? Map.of() : payload);
        String hash = sha256(raw);
        A2ARemoteAuthorityService.AuthorityDecision decision = authority.evaluate(tenant, trackingId, context);

        // Local sequence is append order only; it does not grant remote authority.
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:lockKey,0))",
                new MapSqlParameterSource("lockKey", tenant + "|a2a-remote-sequence|" + trackingId));
        Long next = jdbc.queryForObject(
                "select coalesce(max(local_remote_sequence),0)+1 from a2a_remote_event_journal where tenant_id=:tenant and tracking_id=:tracking",
                new MapSqlParameterSource("tenant", tenant).addValue("tracking", trackingId),
                Long.class);
        long localSequence = next == null ? 1L : next;
        String dedup = remoteEventId == null || remoteEventId.isBlank() ? null : source + ":" + remoteEventId.trim();
        String eventId = "a2a-event-" + UUID.randomUUID();
        try {
            jdbc.update("""
                    insert into a2a_remote_event_journal(
                        tenant_id,journal_event_id,tracking_id,execution_id,remote_task_id,source,event_type,remote_state,
                        remote_event_id,dedup_key,payload_hash,payload_json,local_remote_sequence,stream_id,is_authoritative,
                        authority_epoch,lease_owner_instance_id,lease_token_fingerprint,authority_decision_reason,
                        authority_contract_version,observed_at)
                    values(
                        :tenant,:event,:tracking,:execution,:remoteTask,:source,:type,:state,
                        :remoteEvent,:dedup,:hash,cast(:payload as jsonb),:localSequence,:stream,:authoritative,
                        :epoch,:owner,:tokenFingerprint,:reason,'C0_C1_V1',:now)
                    """, new MapSqlParameterSource("tenant", tenant)
                    .addValue("event", eventId)
                    .addValue("tracking", trackingId)
                    .addValue("execution", executionId)
                    .addValue("remoteTask", remoteTaskId)
                    .addValue("source", source)
                    .addValue("type", eventType)
                    .addValue("state", remoteState)
                    .addValue("remoteEvent", blankToNull(remoteEventId))
                    .addValue("dedup", dedup)
                    .addValue("hash", hash)
                    .addValue("payload", raw)
                    .addValue("localSequence", localSequence)
                    .addValue("stream", context.streamId())
                    .addValue("authoritative", decision.authoritative())
                    .addValue("epoch", context.authorityEpoch())
                    .addValue("owner", blankToNull(context.ownerInstanceId()))
                    .addValue("tokenFingerprint", A2ARemoteAuthorityService.tokenFingerprint(context.leaseToken()))
                    .addValue("reason", decision.reason())
                    .addValue("now", OffsetDateTime.now()));
            return new AppendResult(true, decision.authoritative(), decision.reason(), eventId, localSequence);
        } catch (DuplicateKeyException ex) {
            return new AppendResult(false, false, "DUPLICATE_REMOTE_EVENT_IDENTITY", null, localSequence);
        }
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "a2a-event-journal");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
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

    public record AppendResult(boolean appended, boolean authoritative, String authorityReason, String journalEventId, long localSequence) {}
}
