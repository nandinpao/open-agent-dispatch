package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Accepted Agent callback is positive delivery evidence for a V206 DELIVERY_UNKNOWN intent. */
@Component
public class A0R7DispatchIntentCallbackAcceptedEventHandler implements ModuleEventHandler<TaskCallbackAcceptedEvent> {
    private final NamedParameterJdbcTemplate jdbc;

    public A0R7DispatchIntentCallbackAcceptedEventHandler(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public String eventType() { return TaskCallbackAcceptedEvent.TYPE; }
    @Override public Class<TaskCallbackAcceptedEvent> payloadType() { return TaskCallbackAcceptedEvent.class; }

    @Override
    @Transactional
    public void handle(TaskCallbackAcceptedEvent event) {
        if (event == null || blank(event.tenantId()) || blank(event.dispatchRequestId())) return;
        bind(event.tenantId());
        String ref = "dispatch:" + event.dispatchRequestId();
        IntentRow before = jdbc.query("""
                select intent_id,assignment_id,status
                  from execution_dispatch_intents_v206
                 where tenant_id=:tenant and external_execution_ref=:ref
                """, new MapSqlParameterSource("tenant", event.tenantId()).addValue("ref", ref),
                rs -> rs.next() ? new IntentRow(rs.getString(1), rs.getString(2), rs.getString(3)) : null);
        if (before == null || "ACKNOWLEDGED".equals(before.status())) return;
        if (!("SEND_STARTED".equals(before.status()) || "DELIVERY_UNKNOWN".equals(before.status()) || "DELIVERY_FAILED".equals(before.status()))) return;
        int updated = jdbc.update("""
                update execution_dispatch_intents_v206
                   set status='ACKNOWLEDGED',last_error_code=null,last_error_message=null,updated_at=now()
                 where tenant_id=:tenant and intent_id=:intent
                   and status in ('SEND_STARTED','DELIVERY_UNKNOWN','DELIVERY_FAILED')
                """, new MapSqlParameterSource("tenant", event.tenantId()).addValue("intent", before.intentId()));
        if (updated != 1) return;
        jdbc.update("""
                insert into execution_dispatch_intent_events_v206(
                    tenant_id,event_id,intent_id,assignment_id,from_status,to_status,reason_code,
                    actor_ref,evidence_json,occurred_at)
                values(:tenant,:event,:intent,:assignment,:from,'ACKNOWLEDGED','PC_S2_CALLBACK_PROVES_DELIVERY',
                       'pc-s2-callback-convergence',jsonb_build_object('callbackId',:callback,'callbackType',:type),now())
                """, new MapSqlParameterSource("tenant", event.tenantId())
                .addValue("event", "dispatch-intent-event-" + UUID.randomUUID())
                .addValue("intent", before.intentId()).addValue("assignment", before.assignmentId())
                .addValue("from", before.status()).addValue("callback", event.callbackId())
                .addValue("type", event.callbackType()));
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "pc-s2-callback-convergence");
    }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private record IntentRow(String intentId, String assignmentId, String status) {}
}
