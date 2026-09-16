package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Stage 6/7/8 completion convergence for non-Agent providers.
 * It persists an Artifact and terminal Child Task / CapabilityDelegation evidence, then Stage 5's
 * durable parent-continuation worker delivers the result to the current Parent Agent.
 */
@Service
public class ProviderNeutralCapabilityExecutionCompletionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProviderNeutralCapabilityExecutionCompletionService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void complete(String tenantId, String delegationId, String taskId, String providerType, String providerId,
            String externalExecutionRef, boolean success, Map<String,Object> payload, String errorCode, String errorMessage) {
        bind(tenantId);
        Integer already = jdbc.queryForObject("select count(*) from capability_delegation_requests where tenant_id=:tenant and delegation_id=:delegation and status in ('RESULT_SUCCEEDED','RESULT_FAILED')", new MapSqlParameterSource("tenant",tenantId).addValue("delegation",delegationId), Integer.class);
        if (already != null && already > 0) return;
        OffsetDateTime now = OffsetDateTime.now();
        String body = write(payload == null ? Map.of() : payload);
        String hash = sha256(body);
        String artifactId = "cap-artifact-" + UUID.randomUUID();
        jdbc.update("""
            insert into capability_execution_artifacts(
              tenant_id,artifact_id,delegation_id,task_id,provider_type,provider_id,external_execution_ref,
              artifact_type,media_type,content_json,content_hash,created_at)
            values(:tenant,:artifact,:delegation,:task,:providerType,:provider,:externalRef,'RESULT','application/json',cast(:body as jsonb),:hash,:now)
            """, new MapSqlParameterSource("tenant",tenantId).addValue("artifact",artifactId)
                .addValue("delegation",delegationId).addValue("task",taskId).addValue("providerType",providerType)
                .addValue("provider",providerId).addValue("externalRef",externalExecutionRef).addValue("body",body)
                .addValue("hash",hash).addValue("now",now));

        String terminal = success ? "SUCCEEDED" : "FAILED";
        jdbc.update("""
            update tasks set status=:status,terminal_at=:now,updated_at=:now,
              lifecycle_reason=:reason
            where tenant_id=:tenant and task_id=:task and status not in ('SUCCEEDED','FAILED','CANCELED','DEAD_LETTER')
            """, new MapSqlParameterSource("tenant",tenantId).addValue("task",taskId).addValue("status",terminal)
                .addValue("now",now).addValue("reason",success?"External capability provider completed":"External capability provider failed: "+safe(errorCode)));

        String syntheticCallback = "external:" + externalExecutionRef;
        String notificationId = "cap-result:" + delegationId + ":" + sha256(syntheticCallback).substring(0,24);
        jdbc.update("""
            update capability_delegation_requests
               set status=:delegationStatus,
                   result_status=:resultStatus,
                   result_message=:message,
                   result_error_code=:errorCode,
                   result_error_message=:errorMessage,
                   result_callback_id=coalesce(result_callback_id,:callback),
                   result_callback_fingerprint=coalesce(result_callback_fingerprint,:hash),
                   result_payload_hash=:hash,
                   result_payload_json=cast(:body as jsonb),
                   result_received_at=:now,
                   result_notification_id=coalesce(result_notification_id,:notification),
                   result_notification_status='PENDING',
                   result_notification_next_retry_at=:now,
                   finalization_status='PENDING_NOTIFICATION',
                   terminalization_reason=:terminalReason,
                   updated_at=:now
             where tenant_id=:tenant and delegation_id=:delegation
            """, new MapSqlParameterSource("tenant",tenantId).addValue("delegation",delegationId)
                .addValue("delegationStatus",success?"RESULT_SUCCEEDED":"RESULT_FAILED")
                .addValue("resultStatus",success?"SUCCEEDED":"FAILED")
                .addValue("message",success?"External capability provider completed":"External capability provider failed")
                .addValue("errorCode",errorCode).addValue("errorMessage",errorMessage).addValue("callback",syntheticCallback)
                .addValue("hash",hash).addValue("body",body).addValue("now",now).addValue("notification",notificationId)
                .addValue("terminalReason",success?"EXTERNAL_PROVIDER_RESULT_SUCCEEDED":"EXTERNAL_PROVIDER_RESULT_FAILED"));
        jdbc.update("""
            insert into capability_delegation_events(tenant_id,event_id,delegation_id,event_type,from_status,to_status,reason_codes_json,evidence_json,occurred_at)
            values(:tenant,:event,:delegation,'EXTERNAL_RESULT_ACCEPTED',null,:status,cast(:reasons as jsonb),cast(:evidence as jsonb),:now)
            """, new MapSqlParameterSource("tenant",tenantId).addValue("event","cap-delegation-event-"+UUID.randomUUID())
                .addValue("delegation",delegationId).addValue("status",success?"RESULT_SUCCEEDED":"RESULT_FAILED")
                .addValue("reasons",write(errorCode==null?java.util.List.of():java.util.List.of(errorCode)))
                .addValue("evidence",write(Map.of("providerType",providerType,"providerId",safe(providerId),"artifactId",artifactId,"payloadHash",hash)))
                .addValue("now",now));
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"external-capability-completion");
    }
    private String write(Object v) { try { return json.writeValueAsString(v); } catch(Exception ex) { throw new IllegalStateException("JSON serialization failed",ex); } }
    private static String sha256(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch(Exception ex) { throw new IllegalStateException(ex); } }
    private static String safe(String v) { return v==null?"":v; }
}
