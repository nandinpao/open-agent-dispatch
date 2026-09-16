package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stage 5 runtime authorization envelope for capability-governed assignments. */
@Service
public class CapabilityRuntimeAuthorizationEnvelopeService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityRuntimeAuthorizationEnvelopeService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public String issue(String tenantId, String delegationId, String assignmentId, String bindingId,
            String authorizationDecisionId, String routingDecisionId) {
        bind(tenantId, "capability-runtime-envelope");
        OffsetDateTime leaseExpiry = jdbc.queryForObject("""
            select lease_expires_at from task_assignments
             where tenant_id=:tenant and assignment_id=:assignment
            """, new MapSqlParameterSource("tenant", tenantId).addValue("assignment", assignmentId), OffsetDateTime.class);
        String envelopeId = "cap-auth-envelope-" + UUID.randomUUID();
        jdbc.update("""
            insert into capability_runtime_authorization_envelopes(
                tenant_id,envelope_id,delegation_id,assignment_id,binding_id,authorization_decision_id,
                routing_decision_id,authorization_epoch,revocation_version,status,valid_until,created_at,updated_at)
            values(:tenant,:envelope,:delegation,:assignment,:binding,:authorization,:routing,1,0,'ACTIVE',:validUntil,:at,:at)
            on conflict(tenant_id,delegation_id) do nothing
            """, new MapSqlParameterSource("tenant", tenantId).addValue("envelope", envelopeId)
                .addValue("delegation", delegationId).addValue("assignment", assignmentId).addValue("binding", bindingId)
                .addValue("authorization", authorizationDecisionId).addValue("routing", routingDecisionId)
                .addValue("validUntil", leaseExpiry).addValue("at", OffsetDateTime.now()));
        append(tenantId, delegationId, assignmentId, "AUTHORIZATION_ENVELOPE_ISSUED", "ACTIVE", 1L, 0L,
                Map.of("bindingId", bindingId, "authorizationDecisionId", authorizationDecisionId,
                        "routingDecisionId", routingDecisionId, "validUntil", leaseExpiry == null ? "" : leaseExpiry.toString()));
        return findEnvelopeId(tenantId, delegationId);
    }

    @Transactional
    public String issuePlanStep(String tenantId,String runId,String stepId,String attemptId,String assignmentId,String bindingId,
            String authorizationDecisionId,String routingDecisionId,String parentBindingAuthorizationEnvelopeId) {
        bind(tenantId,"plan-runtime-envelope");
        OffsetDateTime leaseExpiry=jdbc.queryForObject("select lease_expires_at from task_assignments where tenant_id=:tenant and assignment_id=:assignment",
                new MapSqlParameterSource("tenant",tenantId).addValue("assignment",assignmentId),OffsetDateTime.class);
        String envelopeId="plan-auth-envelope-"+UUID.randomUUID();OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
            insert into capability_runtime_authorization_envelopes(
              tenant_id,envelope_id,delegation_id,assignment_id,binding_id,authorization_decision_id,routing_decision_id,
              authorization_epoch,revocation_version,status,valid_until,execution_context_type,plan_run_id,plan_step_id,plan_attempt_id,parent_binding_authorization_envelope_id,created_at,updated_at)
            values(:tenant,:envelope,null,:assignment,:binding,:authorization,:routing,1,0,'ACTIVE',:validUntil,'PLAN_STEP',:run,:step,:attempt,:parent,:at,:at)
            on conflict do nothing
            """,new MapSqlParameterSource("tenant",tenantId).addValue("envelope",envelopeId).addValue("assignment",assignmentId)
                .addValue("binding",bindingId).addValue("authorization",authorizationDecisionId).addValue("routing",routingDecisionId)
                .addValue("validUntil",leaseExpiry).addValue("run",runId).addValue("step",stepId).addValue("attempt",attemptId).addValue("parent",required(parentBindingAuthorizationEnvelopeId,"parentBindingAuthorizationEnvelopeId")).addValue("at",now));
        String persisted=jdbc.queryForObject("select envelope_id from capability_runtime_authorization_envelopes where tenant_id=:tenant and plan_attempt_id=:attempt",
                new MapSqlParameterSource("tenant",tenantId).addValue("attempt",attemptId),String.class);
        if(persisted==null||persisted.isBlank())throw new IllegalStateException("PLAN_RUNTIME_AUTHORIZATION_ENVELOPE_NOT_PERSISTED");
        return persisted;
    }

    @Transactional
    public Envelope revoke(String tenantId, String delegationId, String reason) {
        bind(tenantId, "capability-runtime-revocation");
        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbc.update("""
            update capability_runtime_authorization_envelopes
               set status='REVOKED', revocation_version=revocation_version+1,
                   authorization_epoch=authorization_epoch+1, revoked_at=:at, revocation_reason=:reason, updated_at=:at
             where tenant_id=:tenant and delegation_id=:delegation and status='ACTIVE'
            """, new MapSqlParameterSource("tenant", tenantId).addValue("delegation", delegationId)
                .addValue("at", now).addValue("reason", required(reason, "reason")));
        Envelope envelope = find(tenantId, delegationId);
        if (updated > 0 && envelope != null) {
            append(tenantId, delegationId, envelope.assignmentId(), "AUTHORIZATION_REVOKED", "POLICY_REVOKED",
                    envelope.authorizationEpoch(), envelope.revocationVersion(), Map.of("reason", reason));
        }
        return envelope;
    }

    @Transactional(readOnly=true)
    public Envelope find(String tenantId, String delegationId) {
        bind(tenantId, "capability-runtime-envelope-read");
        try {
            return jdbc.queryForObject("""
                select envelope_id,delegation_id,assignment_id,binding_id,authorization_decision_id,routing_decision_id,
                       authorization_epoch,revocation_version,status,valid_until,revoked_at,revocation_reason
                  from capability_runtime_authorization_envelopes
                 where tenant_id=:tenant and delegation_id=:delegation
                """, new MapSqlParameterSource("tenant", tenantId).addValue("delegation", delegationId),
                    (rs, row) -> new Envelope(rs.getString("envelope_id"), rs.getString("delegation_id"),
                            rs.getString("assignment_id"), rs.getString("binding_id"), rs.getString("authorization_decision_id"),
                            rs.getString("routing_decision_id"), rs.getLong("authorization_epoch"), rs.getLong("revocation_version"),
                            rs.getString("status"), rs.getObject("valid_until", OffsetDateTime.class),
                            rs.getObject("revoked_at", OffsetDateTime.class), rs.getString("revocation_reason")));
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private String findEnvelopeId(String tenantId, String delegationId) {
        Envelope envelope = find(tenantId, delegationId);
        if (envelope == null) throw new IllegalStateException("CAPABILITY_RUNTIME_AUTHORIZATION_ENVELOPE_NOT_PERSISTED");
        return envelope.envelopeId();
    }

    private void append(String tenantId, String delegationId, String assignmentId, String eventType, String reasonCode,
            Long epoch, Long revocation, Map<String,Object> evidence) {
        jdbc.update("""
            insert into capability_runtime_safety_events(
                tenant_id,event_id,delegation_id,assignment_id,event_type,reason_code,authorization_epoch,revocation_version,evidence_json,occurred_at)
            values(:tenant,:event,:delegation,:assignment,:type,:reason,:epoch,:revocation,cast(:evidence as jsonb),:at)
            """, new MapSqlParameterSource("tenant", tenantId).addValue("event", "cap-safety-event-" + UUID.randomUUID())
                .addValue("delegation", delegationId).addValue("assignment", assignmentId).addValue("type", eventType)
                .addValue("reason", reasonCode).addValue("epoch", epoch).addValue("revocation", revocation)
                .addValue("evidence", write(evidence)).addValue("at", OffsetDateTime.now()));
    }

    private void bind(String tenantId, String actor) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Safety evidence serialization failed", ex); }
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public record Envelope(String envelopeId, String delegationId, String assignmentId, String bindingId,
            String authorizationDecisionId, String routingDecisionId, long authorizationEpoch, long revocationVersion,
            String status, OffsetDateTime validUntil, OffsetDateTime revokedAt, String revocationReason) {}
}
