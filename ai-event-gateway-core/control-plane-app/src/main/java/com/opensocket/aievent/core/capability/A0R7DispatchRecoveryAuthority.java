package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryAuthority;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryAuthorityDecision;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PC-S2 recovery authority for the A0-R7 SEND_STARTED crash window.
 *
 * <p>The durable V206 DispatchIntent, not a transport timeout, decides whether an expired worker
 * claim is safe to retry. SEND_STARTED/DELIVERY_UNKNOWN is held for reconciliation; an already
 * ACKNOWLEDGED intent can restore the bridge DispatchRequest without sending again.</p>
 */
@Component
public class A0R7DispatchRecoveryAuthority implements DispatchRecoveryAuthority {
    private static final String AUTHORITY_VERSION = "A0-R7-V206";
    private final NamedParameterJdbcTemplate jdbc;

    public A0R7DispatchRecoveryAuthority(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public int order() { return 50; }

    @Override
    @Transactional
    public DispatchRecoveryAuthorityDecision reconcile(DispatchRequest request, OffsetDateTime now) {
        if (request == null
                || request.getAuthorityProvenance() != DispatchAuthorityProvenance.A0_R7_CANONICAL
                || !AUTHORITY_VERSION.equals(request.getExecutionAuthorityVersion())
                || blank(request.getTenantId()) || blank(request.getDispatchRequestId())) {
            return DispatchRecoveryAuthorityDecision.notApplicable();
        }
        bind(request.getTenantId());
        String externalRef = "dispatch:" + request.getDispatchRequestId();
        IntentRow intent;
        try {
            intent = jdbc.queryForObject("""
                    select intent_id,assignment_id,status
                      from execution_dispatch_intents_v206
                     where tenant_id=:tenant
                       and external_execution_ref=:externalRef
                     for update
                    """,
                    new MapSqlParameterSource("tenant", request.getTenantId())
                            .addValue("externalRef", externalRef),
                    (rs, n) -> new IntentRow(rs.getString("intent_id"), rs.getString("assignment_id"), rs.getString("status")));
        } catch (EmptyResultDataAccessException missing) {
            return DispatchRecoveryAuthorityDecision.notApplicable();
        }
        if (intent == null) return DispatchRecoveryAuthorityDecision.notApplicable();

        if ("ACKNOWLEDGED".equals(intent.status())) {
            return DispatchRecoveryAuthorityDecision.confirmDelivered(
                    "A0_R7_INTENT_ALREADY_ACKNOWLEDGED",
                    "Canonical DispatchIntent proves the gateway delivery was acknowledged; no resend is allowed");
        }
        if ("SEND_STARTED".equals(intent.status()) || "SENT".equals(intent.status())) {
            int updated = jdbc.update("""
                    update execution_dispatch_intents_v206
                       set status='DELIVERY_UNKNOWN',
                           delivery_unknown_since=coalesce(delivery_unknown_since,:now),
                           last_error_code='CORE_CRASH_AFTER_SEND_STARTED',
                           last_error_message='Expired Core claim observed after SEND_STARTED; network outcome is unknown',
                           claimed_by=null,claim_token=null,claim_until=null,updated_at=:now
                     where tenant_id=:tenant and intent_id=:intent and status in ('SEND_STARTED','SENT')
                    """,
                    new MapSqlParameterSource("tenant", request.getTenantId())
                            .addValue("intent", intent.intentId())
                            .addValue("now", now));
            if (updated == 1) {
                append(request.getTenantId(), intent, intent.status(), "DELIVERY_UNKNOWN",
                        "PC_S2_EXPIRED_CLAIM_AFTER_SEND_STARTED", now);
            }
            return DispatchRecoveryAuthorityDecision.holdUnknown(
                    "A0_R7_SEND_STARTED_OUTCOME_UNKNOWN",
                    "SEND_STARTED survived the worker claim; automatic retry/reassignment is suppressed pending callback or reconciliation");
        }
        if ("DELIVERY_UNKNOWN".equals(intent.status())) {
            return DispatchRecoveryAuthorityDecision.holdUnknown(
                    "A0_R7_DELIVERY_OUTCOME_STILL_UNKNOWN",
                    "Canonical DispatchIntent remains DELIVERY_UNKNOWN; automatic retry/reassignment is forbidden");
        }
        return DispatchRecoveryAuthorityDecision.notApplicable();
    }

    private void append(String tenant, IntentRow intent, String from, String to, String reason, OffsetDateTime now) {
        jdbc.update("""
                insert into execution_dispatch_intent_events_v206(
                    tenant_id,event_id,intent_id,assignment_id,from_status,to_status,reason_code,
                    actor_ref,evidence_json,occurred_at)
                values(:tenant,:event,:intent,:assignment,:from,:to,:reason,:actor,
                       jsonb_build_object('productionClosureStage','PC-S2','automaticRetrySuppressed',true),:now)
                """,
                new MapSqlParameterSource("tenant", tenant)
                        .addValue("event", "dispatch-intent-event-" + UUID.randomUUID())
                        .addValue("intent", intent.intentId())
                        .addValue("assignment", intent.assignmentId())
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("reason", reason)
                        .addValue("actor", actor())
                        .addValue("now", now));
    }

    private void bind(String tenant) {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        if (context != null && !"INSTANCE".equalsIgnoreCase(context.tenantId()) && !tenant.equals(context.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for A0-R7 dispatch recovery");
        }
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor());
    }

    private String actor() {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "pc-s2-a0-r7-dispatch-recovery" : context.actorId();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private record IntentRow(String intentId, String assignmentId, String status) {}
}
