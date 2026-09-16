package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Durable Stage 5 retry claim service for parent-Agent result continuation. */
@Service
public class CapabilityDelegationResultRetryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityDelegationResultRetryService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public List<CapabilityDelegationResultNotification> claimDue(String tenantId, String workerId, int limit) {
        bind(tenantId);
        int capped = Math.max(1, Math.min(limit, 100));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime claimUntil = now.plusSeconds(90);
        List<Row> rows = jdbc.query("""
            with due as (
              select delegation_id
                from capability_delegation_requests
               where tenant_id=:tenant
                 and result_callback_id is not null
                 and result_notification_status in ('PENDING','FAILED')
                 and result_notification_attempts < result_notification_max_attempts
                 and (result_notification_next_retry_at is null or result_notification_next_retry_at<=:now)
                 and (result_notification_claim_until is null or result_notification_claim_until<:now)
               order by coalesce(result_notification_next_retry_at,result_received_at,updated_at), delegation_id
               for update skip locked
               limit :limit
            )
            update capability_delegation_requests r
               set result_notification_claimed_by=:worker,
                   result_notification_claim_until=:claimUntil,
                   updated_at=:now
              from due
             where r.tenant_id=:tenant and r.delegation_id=due.delegation_id
            returning r.delegation_id,r.parent_task_id,r.child_task_id,r.capability_code,r.operation,r.status,
                      r.result_status,r.result_message,r.result_error_code,r.result_error_message,
                      r.completed_by_agent_id,r.result_callback_id,r.result_payload_hash,r.correlation_id,
                      r.result_notification_id,r.result_payload_json::text
            """, new MapSqlParameterSource("tenant", tenantId).addValue("worker", workerId)
                .addValue("claimUntil", claimUntil).addValue("now", now).addValue("limit", capped),
            (rs,row) -> new Row(rs.getString("delegation_id"),rs.getString("parent_task_id"),rs.getString("child_task_id"),
                    rs.getString("capability_code"),rs.getString("operation"),rs.getString("status"),rs.getString("result_status"),
                    rs.getString("result_message"),rs.getString("result_error_code"),rs.getString("result_error_message"),
                    rs.getString("completed_by_agent_id"),rs.getString("result_callback_id"),rs.getString("result_payload_hash"),
                    rs.getString("correlation_id"),rs.getString("result_notification_id"),rs.getString("result_payload_json")));
        List<CapabilityDelegationResultNotification> notifications = new ArrayList<>();
        for (Row row : rows) {
            ParentAuthority parent = currentParentAuthority(tenantId,row.parentTaskId());
            if (parent == null) {
                jdbc.update("""
                    update capability_delegation_requests
                       set result_notification_status='FAILED',result_notification_error='PARENT_TASK_HAS_NO_CURRENT_ASSIGNED_AGENT',
                           result_notification_next_retry_at=:retry,result_notification_claimed_by=null,result_notification_claim_until=null,
                           finalization_status='PENDING_NOTIFICATION',finalization_error='PARENT_TASK_HAS_NO_CURRENT_ASSIGNED_AGENT',updated_at=:now
                     where tenant_id=:tenant and delegation_id=:delegation
                    """, new MapSqlParameterSource("tenant",tenantId).addValue("delegation",row.delegationId())
                        .addValue("retry",now.plusSeconds(30)).addValue("now",now));
                append(tenantId,row.delegationId(),"RESULT_NOTIFICATION_RETRY_BLOCKED","PARENT_TASK_HAS_NO_CURRENT_ASSIGNED_AGENT");
                continue;
            }
            jdbc.update("""
                update capability_delegation_requests
                   set result_notification_status='PENDING',result_notification_error=null,
                       result_notified_parent_assignment_id=:assignment,result_notified_agent_id=:agent,
                       result_notified_agent_session_id=:session,result_notified_gateway_node_id=:node,
                       finalization_status='PENDING_NOTIFICATION',finalization_error=null,updated_at=:now
                 where tenant_id=:tenant and delegation_id=:delegation and result_notification_claimed_by=:worker
                """, new MapSqlParameterSource("tenant",tenantId).addValue("delegation",row.delegationId()).addValue("worker",workerId)
                    .addValue("assignment",parent.assignmentId()).addValue("agent",parent.agentId())
                    .addValue("session",parent.agentSessionId()).addValue("node",parent.ownerGatewayNodeId()).addValue("now",now));
            notifications.add(new CapabilityDelegationResultNotification(tenantId,row.delegationId(),row.parentTaskId(),row.childTaskId(),
                    row.capabilityCode(),row.operation(),row.status(),row.resultStatus(),row.resultMessage(),row.errorCode(),row.errorMessage(),
                    row.completedByAgentId(),row.callbackId(),row.payloadHash(),row.correlationId(),parent.assignmentId(),parent.agentId(),
                    parent.agentSessionId(),parent.ownerGatewayNodeId(),row.notificationId(),readPayload(row.payloadJson()),false));
        }
        return List.copyOf(notifications);
    }

    private ParentAuthority currentParentAuthority(String tenantId,String parentTaskId) {
        List<ParentAuthority> rows=jdbc.query("""
            select assignment_id,agent_id,owner_gateway_node_id,agent_session_id
              from task_assignments
             where tenant_id=:tenant and task_id=:task and status='ASSIGNED'
             order by created_at desc limit 1
            """,new MapSqlParameterSource("tenant",tenantId).addValue("task",parentTaskId),
            (rs,row)->new ParentAuthority(rs.getString("assignment_id"),rs.getString("agent_id"),rs.getString("owner_gateway_node_id"),rs.getString("agent_session_id")));
        return rows.isEmpty()?null:rows.getFirst();
    }
    private Map<String,Object> readPayload(String raw) {
        if (raw==null||raw.isBlank()) return Map.of();
        try { return json.readValue(raw,new TypeReference<Map<String,Object>>(){}); }
        catch (Exception ex) { return new LinkedHashMap<>(); }
    }
    private void append(String tenant,String delegation,String type,String reason) {
        jdbc.update("""
          insert into capability_runtime_safety_events(tenant_id,event_id,delegation_id,event_type,reason_code,evidence_json,occurred_at)
          values(:tenant,:event,:delegation,:type,:reason,'{}'::jsonb,:at)
          """,new MapSqlParameterSource("tenant",tenant).addValue("event","cap-safety-event-"+UUID.randomUUID())
                .addValue("delegation",delegation).addValue("type",type).addValue("reason",reason).addValue("at",OffsetDateTime.now()));
    }
    private void bind(String tenantId) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"capability-result-retry");
    }
    private record Row(String delegationId,String parentTaskId,String childTaskId,String capabilityCode,String operation,String status,
            String resultStatus,String resultMessage,String errorCode,String errorMessage,String completedByAgentId,String callbackId,
            String payloadHash,String correlationId,String notificationId,String payloadJson) {}
    private record ParentAuthority(String assignmentId,String agentId,String ownerGatewayNodeId,String agentSessionId) {}
}
