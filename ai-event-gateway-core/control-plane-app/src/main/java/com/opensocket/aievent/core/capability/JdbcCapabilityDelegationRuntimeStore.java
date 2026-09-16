package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** JDBC adapter behind the Stage 6 capability-delegation runtime ports. */
@Repository
public class JdbcCapabilityDelegationRuntimeStore
        implements CapabilityDelegationRuntimeQueryPort, CapabilityDelegationPersistencePort {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcCapabilityDelegationRuntimeStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void requireCurrentAssignment(String tenantId, String taskId, String agentId, String agentSessionId) {
        bind(tenantId);
        Integer count = jdbc.queryForObject("select count(*) from task_assignments where tenant_id=:tenant and task_id=:task and agent_id=:agent and status='ASSIGNED' and (:session is null or :session='' or agent_session_id=:session)",
                new MapSqlParameterSource("tenant", tenantId).addValue("task", taskId).addValue("agent", agentId)
                        .addValue("session", trim(agentSessionId)), Integer.class);
        if (count == null || count != 1) throw new IllegalArgumentException("REQUESTING_AGENT_IS_NOT_CURRENT_TASK_ASSIGNEE");
    }

    @Override
    public RuntimePolicySnapshot activePolicy(String tenantId) {
        bind(tenantId);
        try {
            return jdbc.queryForObject("select routing_profile_id,default_access_mode,operation_access_modes_json,max_candidate_bindings,automatic_attachment_enabled from runtime_step_authority_policies where tenant_id=:tenant and status='ACTIVE'",
                    new MapSqlParameterSource("tenant", tenantId), (rs, n) -> {
                        if (!rs.getBoolean("automatic_attachment_enabled")) throw new IllegalArgumentException("AUTOMATIC_ATTACHMENT_DISABLED");
                        return new RuntimePolicySnapshot(rs.getString("routing_profile_id"), rs.getString("default_access_mode"),
                                readStringMap(rs.getString("operation_access_modes_json")), rs.getInt("max_candidate_bindings"));
                    });
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("RUNTIME_STEP_AUTHORITY_POLICY_NOT_CONFIGURED");
        }
    }

    @Override
    public RequesterContext requester(String tenantId, String agentId) {
        bind(tenantId);
        try {
            return jdbc.queryForObject("select owner_department_id,owner_group_id from agent_profiles where tenant_id=:tenant and agent_id=:agent and approval_status='APPROVED' and enabled=true",
                    new MapSqlParameterSource("tenant", tenantId).addValue("agent", agentId), (rs, n) ->
                            new RequesterContext(rs.getString("owner_department_id"), blank(rs.getString("owner_group_id")) ? List.of() : List.of(rs.getString("owner_group_id"))));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("REQUESTING_AGENT_NOT_CURRENTLY_APPROVED");
        }
    }

    @Override
    public List<ProviderCandidate> whoCan(String tenantId, CapabilityRequirement requirement, String requestingAgentId) {
        bind(tenantId);
        return jdbc.query("""
              select b.binding_id,b.provider_id,p.provider_type from capability_bindings b
              join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
              where b.tenant_id=:tenant and b.capability_code=:cap and b.trust_status='APPROVED' and p.catalog_status='REGISTERED'
                and (b.stale_after is null or b.stale_after>now())
                and (b.supported_operations_json='[]'::jsonb or jsonb_exists(b.supported_operations_json, cast(:op as text)))
                and (
                  (p.provider_type='MANAGED_AGENT' and exists(select 1 from managed_agent_provider_links l where l.tenant_id=b.tenant_id and l.provider_id=b.provider_id and l.status='ACTIVE' and l.agent_id<>:requester))
                  or (p.provider_type='MCP_TOOL' and :op='READ' and exists(select 1 from mcp_tool_provider_links l join mcp_server_registrations s on s.tenant_id=l.tenant_id and s.mcp_server_id=l.mcp_server_id join mcp_tool_registrations mt on mt.tenant_id=l.tenant_id and mt.mcp_tool_id=l.mcp_tool_id where l.tenant_id=b.tenant_id and l.provider_id=b.provider_id and l.status='ACTIVE' and s.status='ACTIVE' and s.trust_status='APPROVED' and mt.status='APPROVED' and mt.governed_side_effect_level='NONE'))
                  or (p.provider_type='REMOTE_A2A_AGENT' and :op='READ' and exists(select 1 from a2a_peer_provider_links l join a2a_peer_registrations ap on ap.tenant_id=l.tenant_id and ap.peer_id=l.peer_id join a2a_peer_interfaces i on i.tenant_id=l.tenant_id and i.interface_id=l.interface_id where l.tenant_id=b.tenant_id and l.provider_id=b.provider_id and l.status='ACTIVE' and ap.status='ACTIVE' and i.status='APPROVED' and i.protocol_binding='HTTP+JSON' and a2a_interface_current_contract_eligible(i.tenant_id,i.interface_id) and a2a_has_assurance_grant(b.tenant_id,l.peer_id,l.interface_id,'PEER','READ_ALLOWED') and a2a_has_assurance_grant(b.tenant_id,l.peer_id,l.interface_id,'INTERFACE','READ_ALLOWED')))
                ) order by b.binding_id
              """, new MapSqlParameterSource("tenant", tenantId).addValue("cap", requirement.capabilityCode())
                .addValue("requester", requestingAgentId).addValue("op", requirement.operation()),
                (rs, n) -> new ProviderCandidate(rs.getString("binding_id"), rs.getString("provider_id"), rs.getString("provider_type")));
    }

    @Override
    public ProviderMetrics metrics(String tenantId, String bindingId) {
        bind(tenantId);
        try {
            return jdbc.queryForObject("select estimated_cost,p95_latency_ms from provider_eligibility_observations where tenant_id=:tenant and binding_id=:binding order by observed_at desc,observation_id desc limit 1",
                    new MapSqlParameterSource("tenant", tenantId).addValue("binding", bindingId),
                    (rs, n) -> new ProviderMetrics(rs.getBigDecimal("estimated_cost"), (Long) rs.getObject("p95_latency_ms")));
        } catch (EmptyResultDataAccessException ex) { return new ProviderMetrics(null, null); }
    }

    @Override
    public AssignmentDispatchReference currentAssignmentDispatch(String tenantId, String childTaskId) {
        bind(tenantId);
        try {
            return jdbc.queryForObject("""
              select a.assignment_id,(select d.dispatch_request_id from dispatch_requests d where d.tenant_id=a.tenant_id and d.assignment_id=a.assignment_id order by d.created_at desc limit 1) dispatch_request_id
              from task_assignments a where a.tenant_id=:tenant and a.task_id=:task and a.status='ASSIGNED' order by a.created_at desc limit 1
              """, new MapSqlParameterSource("tenant", tenantId).addValue("task", childTaskId),
                    (rs, n) -> new AssignmentDispatchReference(rs.getString("assignment_id"), rs.getString("dispatch_request_id")));
        } catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException("CANONICAL_ASSIGNMENT_NOT_FOUND_AFTER_ADAPTER_SUBMIT"); }
    }

    @Override
    public ExistingDelegation findExisting(String tenantId, String parentTaskId, String requestingAgentId, String idempotencyKey) {
        bind(tenantId);
        try {
            return jdbc.queryForObject("select delegation_id,request_digest from capability_delegation_requests where tenant_id=:tenant and parent_task_id=:parent and requesting_agent_id=:agent and idempotency_key=:idem",
                    new MapSqlParameterSource("tenant", tenantId).addValue("parent", parentTaskId).addValue("agent", requestingAgentId).addValue("idem", idempotencyKey),
                    (rs, n) -> new ExistingDelegation(rs.getString("delegation_id"), rs.getString("request_digest")));
        } catch (EmptyResultDataAccessException ex) { return null; }
    }

    @Override
    public void createReceived(String tenantId, String delegationId, String parentTaskId, String requestingAgentId,
            String requestingAgentSessionId, String gatewayNodeId, String idempotencyKey, String requestDigest,
            CapabilityRequirement requirement, String reason, String inputPayloadRef, String sensitivityLevel,
            String correlationId, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("""
          insert into capability_delegation_requests(tenant_id,delegation_id,parent_task_id,requesting_agent_id,requesting_agent_session_id,gateway_node_id,idempotency_key,request_digest,capability_code,operation,requirement_json,reason,input_payload_ref,sensitivity_level,status,correlation_id,created_at,updated_at)
          values(:tenant,:id,:parent,:agent,:session,:node,:idem,:digest,:cap,:op,cast(:req as jsonb),:reason,:payload,:sensitivity,'RECEIVED',:cid,:at,:at)
          """, new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId).addValue("parent", parentTaskId)
                .addValue("agent", requestingAgentId).addValue("session", trim(requestingAgentSessionId)).addValue("node", trim(gatewayNodeId))
                .addValue("idem", idempotencyKey).addValue("digest", requestDigest).addValue("cap", requirement.capabilityCode())
                .addValue("op", requirement.operation()).addValue("req", write(requirement)).addValue("reason", reason)
                .addValue("payload", trim(inputPayloadRef)).addValue("sensitivity", sensitivityLevel).addValue("cid", correlationId).addValue("at", occurredAt));
    }

    @Override
    public void markAuthorized(String tenantId, String delegationId, String authorizationDecisionId, String routingDecisionId,
            String adapterResolutionId, String bindingId, String providerId, String providerType, String executionKind, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("update capability_delegation_requests set status='AUTHORIZED',authorization_decision_id=:auth,routing_decision_id=:routing,adapter_resolution_id=:adapter,selected_binding_id=:binding,selected_provider_id=:provider,selected_provider_type=:providerType,execution_kind=:executionKind,updated_at=:at where tenant_id=:tenant and delegation_id=:id",
                new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId).addValue("auth", authorizationDecisionId)
                        .addValue("routing", routingDecisionId).addValue("adapter", adapterResolutionId).addValue("binding", bindingId)
                        .addValue("provider", providerId).addValue("providerType", providerType).addValue("executionKind", executionKind).addValue("at", occurredAt));
    }

    @Override
    public void markChildCreated(String tenantId, String delegationId, String childTaskId, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("update capability_delegation_requests set status='CHILD_CREATED',child_task_id=:child,updated_at=:at where tenant_id=:tenant and delegation_id=:id",
                new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId).addValue("child", childTaskId).addValue("at", occurredAt));
    }

    @Override
    public void markDispatchQueued(String tenantId, String delegationId, String assignmentId, String dispatchRequestId,
            List<String> reasonCodes, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("update capability_delegation_requests set status='DISPATCH_QUEUED',assignment_id=:assignment,dispatch_request_id=:dispatch,reason_codes_json=cast(:reasons as jsonb),updated_at=:at where tenant_id=:tenant and delegation_id=:id",
                new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId).addValue("assignment", assignmentId)
                        .addValue("dispatch", dispatchRequestId).addValue("reasons", write(reasonCodes)).addValue("at", occurredAt));
    }

    @Override
    public void stop(String tenantId, String delegationId, String status, List<String> reasonCodes,
            String authorizationDecisionId, String routingDecisionId, String adapterResolutionId,
            String bindingId, String providerId, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("update capability_delegation_requests set status=:status,authorization_decision_id=coalesce(:auth,authorization_decision_id),routing_decision_id=coalesce(:routing,routing_decision_id),adapter_resolution_id=coalesce(:adapter,adapter_resolution_id),selected_binding_id=coalesce(:binding,selected_binding_id),selected_provider_id=coalesce(:provider,selected_provider_id),reason_codes_json=cast(:reasons as jsonb),updated_at=:at where tenant_id=:tenant and delegation_id=:id",
                new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId).addValue("status", status)
                        .addValue("auth", authorizationDecisionId).addValue("routing", routingDecisionId).addValue("adapter", adapterResolutionId)
                        .addValue("binding", bindingId).addValue("provider", providerId).addValue("reasons", write(reasonCodes)).addValue("at", occurredAt));
    }

    @Override
    public CapabilityDelegationReceipt receipt(String tenantId, String delegationId) {
        bind(tenantId);
        Map<String,Object> m = jdbc.queryForMap("select * from capability_delegation_requests where tenant_id=:tenant and delegation_id=:id",
                new MapSqlParameterSource("tenant", tenantId).addValue("id", delegationId));
        return new CapabilityDelegationReceipt(delegationId, str(m,"status"), str(m,"parent_task_id"), str(m,"child_task_id"),
                str(m,"assignment_id"), str(m,"dispatch_request_id"), str(m,"authorization_decision_id"),
                str(m,"routing_decision_id"), str(m,"adapter_resolution_id"), readStrings(str(m,"reason_codes_json")));
    }

    @Override
    public void appendEvent(String tenantId, String delegationId, String eventType, String fromStatus, String toStatus,
            List<String> reasonCodes, Map<String, Object> evidence, OffsetDateTime occurredAt) {
        bind(tenantId);
        jdbc.update("insert into capability_delegation_events(tenant_id,event_id,delegation_id,event_type,from_status,to_status,reason_codes_json,evidence_json,occurred_at) values(:tenant,:event,:id,:type,:from,:to,cast(:reasons as jsonb),cast(:evidence as jsonb),:at)",
                new MapSqlParameterSource("tenant", tenantId).addValue("event", "cap-delegation-event-"+UUID.randomUUID())
                        .addValue("id", delegationId).addValue("type", eventType).addValue("from", fromStatus).addValue("to", toStatus)
                        .addValue("reasons", write(reasonCodes)).addValue("evidence", write(evidence)).addValue("at", occurredAt));
    }

    private void bind(String tenantId) {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        if (context != null && !"INSTANCE".equalsIgnoreCase(context.tenantId()) && !tenantId.equals(context.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch");
        }
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor());
    }

    private String actor() {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "managed-capability-delegation" : context.actorId();
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalArgumentException("JSON serialization failed", ex); } }
    @SuppressWarnings("unchecked") private Map<String,String> readStringMap(String value) { try { return blank(value) ? Map.of() : json.readValue(value, Map.class); } catch (Exception ex) { throw new IllegalStateException("Operation access map invalid", ex); } }
    @SuppressWarnings("unchecked") private List<String> readStrings(String value) { try { return blank(value) ? List.of() : json.readValue(value, List.class); } catch (Exception ex) { return List.of(); } }
    private static String trim(String value) { return blank(value) ? null : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String str(Map<String,Object> values, String key) { Object value=values.get(key); return value==null?null:String.valueOf(value); }
}
