package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Records notification outcome in a transaction separate from terminal result acceptance. */
@Service
public class CapabilityDelegationResultNotificationRecorder {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityDelegationResultNotificationRecorder(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void record(CapabilityDelegationResultNotification n, CapabilityDelegationResultNotifier.NotificationDeliveryResult delivery) {
        if (n == null || delivery == null || n.alreadyDelivered()) return;
        bind(n.tenantId());
        String status = delivery.delivered() ? "DELIVERED" : "FAILED";
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime retryAt = now.plusSeconds(30);
        jdbc.update("""
            update capability_delegation_requests
               set result_notification_status=:status,
                   result_notification_attempts=result_notification_attempts+1,
                   result_notification_error=:error,
                   result_notification_next_retry_at=case
                       when :delivered then null
                       when result_notification_attempts+1 >= result_notification_max_attempts then null
                       else :retryAt end,
                   result_notification_claimed_by=null,
                   result_notification_claim_until=null,
                   result_notified_at=case when :delivered then :at else result_notified_at end,
                   finalization_status=case
                       when :delivered then 'COMPLETED'
                       when result_notification_attempts+1 >= result_notification_max_attempts then 'FAILED'
                       else 'PENDING_NOTIFICATION' end,
                   finalized_at=case when :delivered then :at else finalized_at end,
                   finalization_error=case when :delivered then null else :error end,
                   updated_at=:at
             where tenant_id=:tenant and delegation_id=:delegation and result_notification_id=:notification
            """, params(n).addValue("status", status).addValue("error", delivery.delivered() ? null : safe(delivery.status(), delivery.message()))
                .addValue("delivered", delivery.delivered()).addValue("retryAt", retryAt).addValue("at", now));
        jdbc.update("""
            insert into capability_delegation_events(
                tenant_id,event_id,delegation_id,event_type,from_status,to_status,reason_codes_json,evidence_json,occurred_at)
            values(:tenant,:event,:delegation,:eventType,:taskStatus,:taskStatus,cast(:reasons as jsonb),cast(:evidence as jsonb),:at)
            """, params(n).addValue("event", "cap-delegation-event-" + UUID.randomUUID())
                .addValue("eventType", delivery.delivered() ? "RESULT_NOTIFICATION_DELIVERED" : "RESULT_NOTIFICATION_FAILED")
                .addValue("taskStatus", n.delegationStatus())
                .addValue("reasons", write(delivery.delivered() ? List.of("CURRENT_PARENT_AGENT_NOTIFIED") : List.of("PARENT_RESULT_NOTIFICATION_DELIVERY_FAILED")))
                .addValue("evidence", write(Map.of(
                        "notificationId", n.notificationId(),
                        "parentTaskId", n.parentTaskId(),
                        "parentAssignmentId", n.parentAssignmentId(),
                        "parentAgentId", n.parentAgentId(),
                        "parentGatewayNodeId", n.parentGatewayNodeId() == null ? "" : n.parentGatewayNodeId(),
                        "deliveryStatus", delivery.status() == null ? "" : delivery.status())))
                .addValue("at", now));
    }

    private MapSqlParameterSource params(CapabilityDelegationResultNotification n) {
        return new MapSqlParameterSource("tenant", n.tenantId()).addValue("delegation", n.delegationId())
                .addValue("notification", n.notificationId());
    }
    private void bind(String tenantId) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class,
                "capability-delegation-result-notifier");
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Capability delegation notification evidence serialization failed", ex); }
    }
    private static String safe(String status, String message) {
        String text = (status == null ? "" : status) + (message == null || message.isBlank() ? "" : ": " + message);
        return text.length() <= 2000 ? text : text.substring(0, 2000);
    }
}
