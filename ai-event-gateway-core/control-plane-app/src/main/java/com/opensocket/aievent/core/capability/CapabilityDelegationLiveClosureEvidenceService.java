package com.opensocket.aievent.core.capability;

import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only evidence projection for one canonical managed-Agent Capability Delegation live run.
 *
 * <p>This service is deliberately not an A2A mutation authority. It exposes only IDs/status needed
 * by the Gateway/TestLab certification path to prove that the already-created canonical delegation
 * crossed Child Task -> Assignment -> Dispatch -> Provider callback -> Parent result delivery.</p>
 */
@Service
public class CapabilityDelegationLiveClosureEvidenceService {
    private final NamedParameterJdbcTemplate jdbc;

    public CapabilityDelegationLiveClosureEvidenceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ClosureEvidence find(String tenantId, String parentTaskId, String delegationId) {
        String tenant = required(tenantId, "tenantId");
        String parent = required(parentTaskId, "parentTaskId");
        String delegation = required(delegationId, "delegationId");
        bind(tenant);
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenant)
                .addValue("parent", parent).addValue("delegation", delegation);
        try {
            return jdbc.queryForObject("""
                    select d.delegation_id,d.parent_task_id,d.child_task_id,d.assignment_id,d.dispatch_request_id,
                           d.status,d.selected_provider_id,d.selected_provider_type,d.execution_kind,
                           d.result_callback_id,d.result_status,d.result_notification_status,
                           d.result_notification_attempts,d.result_notified_parent_assignment_id,
                           d.result_notified_agent_id,d.completed_by_agent_id,
                           coalesce(ct.status,'') child_task_status,
                           coalesce(dr.status,'') child_dispatch_status,
                           coalesce(dr.agent_id,'') child_dispatch_agent_id,
                           coalesce(cb.accepted,false) callback_accepted,
                           coalesce(cb.callback_type,'') callback_type,
                           coalesce(cb.agent_id,'') callback_agent_id
                      from capability_delegation_requests d
                      left join tasks ct on ct.tenant_id=d.tenant_id and ct.task_id=d.child_task_id
                      left join dispatch_requests dr on dr.dispatch_request_id=d.dispatch_request_id
                      left join task_callbacks cb on cb.callback_id=d.result_callback_id and cb.task_id=d.child_task_id
                     where d.tenant_id=:tenant and d.parent_task_id=:parent and d.delegation_id=:delegation
                    """, p, (rs, n) -> {
                        String status = rs.getString("status");
                        String notification = rs.getString("result_notification_status");
                        boolean callbackAccepted = rs.getBoolean("callback_accepted");
                        String callbackType = rs.getString("callback_type");
                        String completedBy = rs.getString("completed_by_agent_id");
                        String callbackAgent = rs.getString("callback_agent_id");
                        boolean terminal = "RESULT_SUCCEEDED".equals(status) || "RESULT_FAILED".equals(status);
                        String childTaskId = rs.getString("child_task_id");
                        String assignmentId = rs.getString("assignment_id");
                        String dispatchRequestId = rs.getString("dispatch_request_id");
                        String dispatchAgent = rs.getString("child_dispatch_agent_id");
                        boolean closureComplete = terminal
                                && notBlank(childTaskId) && notBlank(assignmentId) && notBlank(dispatchRequestId)
                                && callbackAccepted
                                && ("RESULT".equals(callbackType) || "ERROR".equals(callbackType))
                                && "DELIVERED".equals(notification)
                                && notBlank(completedBy)
                                && completedBy.equals(callbackAgent)
                                && completedBy.equals(dispatchAgent);
                        return new ClosureEvidence(
                                rs.getString("delegation_id"), rs.getString("parent_task_id"),
                                rs.getString("child_task_id"), rs.getString("assignment_id"),
                                rs.getString("dispatch_request_id"), status,
                                rs.getString("selected_provider_id"), rs.getString("selected_provider_type"),
                                rs.getString("execution_kind"), rs.getString("child_task_status"),
                                rs.getString("child_dispatch_status"), rs.getString("child_dispatch_agent_id"),
                                rs.getString("result_callback_id"), callbackAccepted, callbackType, callbackAgent,
                                completedBy, rs.getString("result_status"), notification,
                                rs.getInt("result_notification_attempts"),
                                rs.getString("result_notified_parent_assignment_id"),
                                rs.getString("result_notified_agent_id"), terminal, closureComplete);
                    });
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Capability delegation closure evidence not found: " + delegation);
        }
    }

    private void bind(String tenantId) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class,
                "capability-delegation-live-closure-evidence");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }

    public record ClosureEvidence(
            String delegationId,
            String parentTaskId,
            String childTaskId,
            String assignmentId,
            String dispatchRequestId,
            String status,
            String selectedProviderId,
            String selectedProviderType,
            String executionKind,
            String childTaskStatus,
            String childDispatchStatus,
            String childDispatchAgentId,
            String resultCallbackId,
            boolean callbackAccepted,
            String callbackType,
            String callbackAgentId,
            String completedByAgentId,
            String resultStatus,
            String resultNotificationStatus,
            int resultNotificationAttempts,
            String resultNotifiedParentAssignmentId,
            String resultNotifiedAgentId,
            boolean terminal,
            boolean closureComplete) {}
}
