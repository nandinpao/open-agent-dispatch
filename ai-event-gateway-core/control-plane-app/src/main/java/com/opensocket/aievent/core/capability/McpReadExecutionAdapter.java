package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.GovernedExternalProviderAssignmentRequest;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stage 6 HOW adapter: create provider-neutral Assignment and durable MCP READ work; no HTTP before commit. */
@Component
public class McpReadExecutionAdapter implements ExecutionAdapterPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TaskOrchestrationFacade tasks;

    public McpReadExecutionAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper json, TaskOrchestrationFacade tasks) {
        this.jdbc=jdbc; this.json=json; this.tasks=tasks;
    }

    @Override public String adapterType() { return "MCP_TOOL"; }

    @Override
    @Transactional
    public ExecutionAdapterResult submit(ExecutionAdapterRegistration adapter, ExecutionAdapterCommand command) {
        if (!"READ".equalsIgnoreCase(command.operation())) {
            return new ExecutionAdapterResult(false,"REJECTED",null,List.of("MCP_STAGE6_READ_ONLY"),OffsetDateTime.now());
        }
        Link link=link(command.tenantId(),adapter.providerId());
        if (link==null) return new ExecutionAdapterResult(false,"REJECTED",null,List.of("MCP_PROVIDER_LINK_NOT_APPROVED"),OffsetDateTime.now());
        if (link.credentialRef()!=null && !link.credentialRef().isBlank())
            return new ExecutionAdapterResult(false,"REJECTED",null,List.of("MCP_CREDENTIAL_RESOLVER_NOT_AVAILABLE_STAGE6"),OffsetDateTime.now());
        String binding=required(command.input().get("bindingId"),"bindingId");
        String auth=required(command.input().get("authorizationDecisionId"),"authorizationDecisionId");
        String routing=required(command.input().get("routingDecisionId"),"routingDecisionId");
        AssignmentDecisionResult assignment=tasks.assignGovernedExternalProvider(new GovernedExternalProviderAssignmentRequest(
                command.localTaskId(),"MCP_TOOL","MCP_TOOL",adapter.providerId(),binding,auth,routing,command.executionResolutionId(),
                null,link.serverId(),link.toolId(),"Stage 6 MCP READ provider selected server-side"));
        if (assignment.assignmentId()==null) return new ExecutionAdapterResult(false,"REJECTED",null,List.of("MCP_EXECUTION_ASSIGNMENT_NOT_CREATED"),OffsetDateTime.now());
        String contextType=String.valueOf(command.input().getOrDefault("executionContextType","DELEGATION"));
        String delegation="PLAN_STEP".equals(contextType)?null:required(command.input().get("delegationId"),"delegationId");
        String planRunId="PLAN_STEP".equals(contextType)?required(command.input().get("planRunId"),"planRunId"):null;
        String planStepId="PLAN_STEP".equals(contextType)?required(command.input().get("planStepId"),"planStepId"):null;
        String planAttemptId="PLAN_STEP".equals(contextType)?required(command.input().get("planAttemptId"),"planAttemptId"):null;
        String executionId="mcp-read-"+UUID.randomUUID();
        Map<String,Object> params = command.input().get("arguments") instanceof Map<?,?> m ? castMap(m) : command.input();
        Map<String,Object> request=Map.of("jsonrpc","2.0","id",executionId,"method","tools/call",
                "params",Map.of("name",link.toolName(),"arguments",params));
        jdbc.update("""
          insert into mcp_read_executions(tenant_id,execution_id,delegation_id,execution_context_type,plan_run_id,plan_step_id,plan_attempt_id,task_id,assignment_id,provider_id,mcp_server_id,mcp_tool_id,endpoint_url,request_json,status,created_at,updated_at)
          values(:tenant,:execution,:delegation,:contextType,:planRun,:planStep,:planAttempt,:task,:assignment,:provider,:server,:tool,:url,cast(:request as jsonb),'QUEUED',:now,:now)
          """, new MapSqlParameterSource("tenant",command.tenantId()).addValue("execution",executionId).addValue("delegation",delegation).addValue("contextType",contextType).addValue("planRun",planRunId).addValue("planStep",planStepId).addValue("planAttempt",planAttemptId)
            .addValue("task",command.localTaskId()).addValue("assignment",assignment.assignmentId()).addValue("provider",adapter.providerId())
            .addValue("server",link.serverId()).addValue("tool",link.toolId()).addValue("url",link.baseUrl()).addValue("request",write(request)).addValue("now",OffsetDateTime.now()));
        return new ExecutionAdapterResult(true,"QUEUED",executionId,List.of("MCP_READ_QUEUED_NO_AGENT_DISPATCH"),OffsetDateTime.now());
    }

    private Link link(String tenant,String provider) {
        try { return jdbc.queryForObject("""
          select l.mcp_server_id,l.mcp_tool_id,s.base_url,s.credential_ref,t.tool_name
            from mcp_tool_provider_links l
            join mcp_server_registrations s on s.tenant_id=l.tenant_id and s.mcp_server_id=l.mcp_server_id
            join mcp_tool_registrations t on t.tenant_id=l.tenant_id and t.mcp_tool_id=l.mcp_tool_id
           where l.tenant_id=:tenant and l.provider_id=:provider and l.status='ACTIVE'
             and s.status='ACTIVE' and s.trust_status='APPROVED' and s.protocol='MCP_STREAMABLE_HTTP' and s.protocol_version='2026-07-28'
             and t.status='APPROVED' and t.governed_side_effect_level='NONE'
          """,new MapSqlParameterSource("tenant",tenant).addValue("provider",provider),
          (rs,n)->new Link(rs.getString("mcp_server_id"),rs.getString("mcp_tool_id"),rs.getString("base_url"),rs.getString("credential_ref"),rs.getString("tool_name"))); }
        catch(EmptyResultDataAccessException ex){ return null; }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Map<?,?> m){ return (Map<String,Object>)(Map<?,?>)m; }
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalStateException("MCP request JSON failed",ex);}}
    private static String required(Object v,String f){if(v==null||String.valueOf(v).isBlank())throw new IllegalArgumentException(f+" is required");return String.valueOf(v);}
    private record Link(String serverId,String toolId,String baseUrl,String credentialRef,String toolName){}
}
