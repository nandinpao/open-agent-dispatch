package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Stage 4 terminal-result acceptance for capability delegation.
 *
 * <p>The canonical Task callback has already been accepted by Execution Control before this service
 * runs. This service only projects that committed callback into capability-delegation evidence and
 * prepares a parent-Agent notification. It never changes Task callback authority.</p>
 */
@Service
public class CapabilityDelegationResultAcceptanceService {
    private static final Logger log = LoggerFactory.getLogger(CapabilityDelegationResultAcceptanceService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityDelegationResultAcceptanceService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public CapabilityDelegationResultNotification accept(TaskCallbackAcceptedEvent callback) {
        if (callback == null || !terminal(callback.callbackType()) || blank(callback.tenantId()) || blank(callback.taskId())) {
            return null;
        }
        bind(callback.tenantId());
        Delegation delegation = findByChildTask(callback.tenantId(), callback.taskId());
        if (delegation == null) return null;

        if (!matches(delegation.assignmentId(), callback.assignmentId())
                || !matches(delegation.dispatchRequestId(), callback.dispatchRequestId())) {
            appendEvent(callback.tenantId(), delegation.delegationId(), "RESULT_REJECTED_STALE_EXECUTION",
                    delegation.status(), delegation.status(),
                    List.of("CALLBACK_EXECUTION_EVIDENCE_DOES_NOT_MATCH_DELEGATION"),
                    Map.of("callbackId", safe(callback.callbackId()),
                            "callbackAssignmentId", safe(callback.assignmentId()),
                            "callbackDispatchRequestId", safe(callback.dispatchRequestId())));
            log.warn("capability_delegation_result_rejected_stale_execution delegationId={} childTaskId={} callbackId={}",
                    delegation.delegationId(), callback.taskId(), callback.callbackId());
            return null;
        }

        if (!blank(delegation.resultCallbackId()) && !delegation.resultCallbackId().equals(callback.callbackId())) {
            appendEvent(callback.tenantId(), delegation.delegationId(), "RESULT_CONFLICT_QUARANTINED",
                    delegation.status(), delegation.status(), List.of("SECOND_TERMINAL_CALLBACK_REJECTED"),
                    Map.of("acceptedCallbackId", delegation.resultCallbackId(), "conflictingCallbackId", safe(callback.callbackId())));
            log.warn("capability_delegation_result_conflict delegationId={} acceptedCallbackId={} conflictingCallbackId={}",
                    delegation.delegationId(), delegation.resultCallbackId(), callback.callbackId());
            return null;
        }

        ParentAuthority parent = currentParentAuthority(callback.tenantId(), delegation.parentTaskId());
        String terminalStatus = successful(callback) ? "RESULT_SUCCEEDED" : "RESULT_FAILED";
        String notificationId = "cap-result:" + delegation.delegationId() + ":" + required(callback.callbackId(), "callbackId");
        boolean alreadyDelivered = "DELIVERED".equals(delegation.resultNotificationStatus());

        if (blank(delegation.resultCallbackId())) {
            String notificationStatus = parent == null ? "FAILED" : "PENDING";
            String notificationError = parent == null ? "PARENT_TASK_HAS_NO_CURRENT_ASSIGNED_AGENT" : null;
            jdbc.update("""
                update capability_delegation_requests
                   set status=:status,
                       result_callback_id=:callback,
                       result_callback_fingerprint=:fingerprint,
                       result_payload_hash=:payloadHash,
                       result_payload_json=cast(:payloadJson as jsonb),
                       result_status=:resultStatus,
                       result_message=:message,
                       result_error_code=:errorCode,
                       result_error_message=:errorMessage,
                       completed_by_agent_id=:completedBy,
                       result_assignment_id=:resultAssignment,
                       result_dispatch_request_id=:resultDispatch,
                       result_received_at=:receivedAt,
                       result_notification_id=:notificationId,
                       result_notification_status=:notificationStatus,
                       result_notification_error=:notificationError,
                       result_notification_next_retry_at=case when :notificationStatus='PENDING' then :receivedAt else null end,
                       finalization_status=case when :notificationStatus='PENDING' then 'PENDING_NOTIFICATION' else 'FAILED' end,
                       terminalization_reason=:terminalizationReason,
                       finalization_error=:notificationError,
                       result_notified_parent_assignment_id=:parentAssignment,
                       result_notified_agent_id=:parentAgent,
                       result_notified_agent_session_id=:parentSession,
                       result_notified_gateway_node_id=:parentNode,
                       updated_at=:receivedAt
                 where tenant_id=:tenant and delegation_id=:delegation
                """, params(callback.tenantId(), delegation.delegationId())
                    .addValue("status", terminalStatus)
                    .addValue("callback", callback.callbackId())
                    .addValue("fingerprint", callback.callbackFingerprint())
                    .addValue("payloadHash", callback.payloadHash())
                    .addValue("payloadJson", write(callback.payload() == null ? Map.of() : callback.payload()))
                    .addValue("resultStatus", first(callback.resultStatus(), callback.callbackType()))
                    .addValue("message", first(callback.message(), callback.errorMessage()))
                    .addValue("errorCode", callback.errorCode())
                    .addValue("errorMessage", callback.errorMessage())
                    .addValue("completedBy", callback.agentId())
                    .addValue("resultAssignment", callback.assignmentId())
                    .addValue("resultDispatch", callback.dispatchRequestId())
                    .addValue("receivedAt", first(callback.acceptedAt(), OffsetDateTime.now()))
                    .addValue("notificationId", notificationId)
                    .addValue("notificationStatus", notificationStatus)
                    .addValue("notificationError", notificationError)
                    .addValue("terminalizationReason", successful(callback) ? "PLAN_COMPLETED" : "PLAN_FAILED")
                    .addValue("parentAssignment", parent == null ? null : parent.assignmentId())
                    .addValue("parentAgent", parent == null ? null : parent.agentId())
                    .addValue("parentSession", parent == null ? null : parent.agentSessionId())
                    .addValue("parentNode", parent == null ? null : parent.ownerGatewayNodeId()));

            appendEvent(callback.tenantId(), delegation.delegationId(), "RESULT_ACCEPTED",
                    delegation.status(), terminalStatus,
                    List.of("CANONICAL_CHILD_CALLBACK_ALREADY_ACCEPTED", "RESULT_EVIDENCE_PROJECTED"),
                    Map.of("callbackId", callback.callbackId(),
                            "childTaskId", callback.taskId(),
                            "resultStatus", safe(first(callback.resultStatus(), callback.callbackType())),
                            "payloadHash", safe(callback.payloadHash()),
                            "completedByAgentId", safe(callback.agentId())));
            if (parent == null) {
                appendEvent(callback.tenantId(), delegation.delegationId(), "RESULT_NOTIFICATION_BLOCKED",
                        terminalStatus, terminalStatus, List.of("PARENT_TASK_HAS_NO_CURRENT_ASSIGNED_AGENT"),
                        Map.of("parentTaskId", delegation.parentTaskId()));
                return null;
            }
        } else if (!alreadyDelivered) {
            // Callback replay is allowed to refresh the *current* parent execution authority before retrying.
            if (parent == null) return null;
            jdbc.update("""
                update capability_delegation_requests
                   set result_notification_status='PENDING',
                       result_notification_error=null,
                       result_notification_next_retry_at=:at,
                       finalization_status='PENDING_NOTIFICATION',
                       finalization_error=null,
                       result_notified_parent_assignment_id=:parentAssignment,
                       result_notified_agent_id=:parentAgent,
                       result_notified_agent_session_id=:parentSession,
                       result_notified_gateway_node_id=:parentNode,
                       updated_at=:at
                 where tenant_id=:tenant and delegation_id=:delegation
                """, params(callback.tenantId(), delegation.delegationId())
                    .addValue("parentAssignment", parent.assignmentId())
                    .addValue("parentAgent", parent.agentId())
                    .addValue("parentSession", parent.agentSessionId())
                    .addValue("parentNode", parent.ownerGatewayNodeId())
                    .addValue("at", OffsetDateTime.now()));
        }

        if (alreadyDelivered) return notification(callback, delegation, parent, terminalStatus, notificationId, true);
        return notification(callback, delegation, parent, terminalStatus, notificationId, false);
    }

    private CapabilityDelegationResultNotification notification(TaskCallbackAcceptedEvent callback, Delegation delegation,
            ParentAuthority parent, String terminalStatus, String notificationId, boolean delivered) {
        if (parent == null) return null;
        return new CapabilityDelegationResultNotification(
                callback.tenantId(), delegation.delegationId(), delegation.parentTaskId(), callback.taskId(),
                delegation.capabilityCode(), delegation.operation(), terminalStatus,
                first(callback.resultStatus(), callback.callbackType()), first(callback.message(), callback.errorMessage()),
                callback.errorCode(), callback.errorMessage(), callback.agentId(), callback.callbackId(), callback.payloadHash(),
                first(callback.correlationId(), delegation.correlationId()), parent.assignmentId(), parent.agentId(),
                parent.agentSessionId(), parent.ownerGatewayNodeId(), notificationId, callback.payload(), delivered);
    }

    private Delegation findByChildTask(String tenantId, String taskId) {
        try {
            return jdbc.queryForObject("""
                select delegation_id,parent_task_id,capability_code,operation,status,assignment_id,dispatch_request_id,
                       result_callback_id,result_notification_status,correlation_id
                  from capability_delegation_requests
                 where tenant_id=:tenant and child_task_id=:task
                 for update
                """, new MapSqlParameterSource("tenant", tenantId).addValue("task", taskId),
                    (rs, row) -> new Delegation(rs.getString("delegation_id"), rs.getString("parent_task_id"),
                            rs.getString("capability_code"), rs.getString("operation"), rs.getString("status"),
                            rs.getString("assignment_id"), rs.getString("dispatch_request_id"),
                            rs.getString("result_callback_id"), rs.getString("result_notification_status"),
                            rs.getString("correlation_id")));
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private ParentAuthority currentParentAuthority(String tenantId, String parentTaskId) {
        try {
            return jdbc.queryForObject("""
                select assignment_id,agent_id,owner_gateway_node_id,agent_session_id
                  from task_assignments
                 where tenant_id=:tenant and task_id=:task and status='ASSIGNED'
                 order by created_at desc limit 1
                """, new MapSqlParameterSource("tenant", tenantId).addValue("task", parentTaskId),
                    (rs, row) -> new ParentAuthority(rs.getString("assignment_id"), rs.getString("agent_id"),
                            rs.getString("owner_gateway_node_id"), rs.getString("agent_session_id")));
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void appendEvent(String tenantId, String delegationId, String type, String from, String to,
            List<String> reasons, Map<String, Object> evidence) {
        jdbc.update("""
            insert into capability_delegation_events(
                tenant_id,event_id,delegation_id,event_type,from_status,to_status,reason_codes_json,evidence_json,occurred_at)
            values(:tenant,:event,:delegation,:type,:from,:to,cast(:reasons as jsonb),cast(:evidence as jsonb),:at)
            """, params(tenantId, delegationId)
                .addValue("event", "cap-delegation-event-" + UUID.randomUUID())
                .addValue("type", type).addValue("from", from).addValue("to", to)
                .addValue("reasons", write(reasons)).addValue("evidence", write(evidence))
                .addValue("at", OffsetDateTime.now()));
    }

    private void bind(String tenantId) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class,
                "capability-delegation-result");
    }

    private MapSqlParameterSource params(String tenantId, String delegationId) {
        return new MapSqlParameterSource("tenant", tenantId).addValue("delegation", delegationId);
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Capability delegation result evidence serialization failed", ex); }
    }
    private static boolean terminal(String type) { return "RESULT".equalsIgnoreCase(type) || "ERROR".equalsIgnoreCase(type); }
    private static boolean successful(TaskCallbackAcceptedEvent e) {
        if (!"RESULT".equalsIgnoreCase(e.callbackType())) return false;
        String s = e.resultStatus();
        return s == null || !(s.equalsIgnoreCase("FAILED") || s.equalsIgnoreCase("ERROR")
                || s.equalsIgnoreCase("CANCELLED") || s.equalsIgnoreCase("REJECTED"));
    }
    private static boolean matches(String expected, String actual) { return blank(expected) || expected.equals(actual); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String required(String value, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String first(String... values) {
        if (values != null) for (String value : values) if (!blank(value)) return value;
        return null;
    }
    private static OffsetDateTime first(OffsetDateTime value, OffsetDateTime fallback) { return value == null ? fallback : value; }

    private record Delegation(String delegationId, String parentTaskId, String capabilityCode, String operation,
            String status, String assignmentId, String dispatchRequestId, String resultCallbackId,
            String resultNotificationStatus, String correlationId) {}
    private record ParentAuthority(String assignmentId, String agentId, String ownerGatewayNodeId, String agentSessionId) {}
}
