package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.GovernedExternalProviderAssignmentRequest;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stage 7 HOW adapter: durable A2A v1 READ submission with provider-neutral Assignment. */
@Component
public class A2ARemoteReadExecutionAdapter implements ExecutionAdapterPort {
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json; private final TaskOrchestrationFacade tasks; private final A2ATrustAssurancePolicyService assurance; private final A2AVersionPolicyService versions; private final A2AExternalF0SecurityService security;
    public A2ARemoteReadExecutionAdapter(NamedParameterJdbcTemplate jdbc,ObjectMapper json,TaskOrchestrationFacade tasks,A2ATrustAssurancePolicyService assurance,A2AVersionPolicyService versions,A2AExternalF0SecurityService security){this.jdbc=jdbc;this.json=json;this.tasks=tasks;this.assurance=assurance;this.versions=versions;this.security=security;}
    @Override public String adapterType(){return "REMOTE_A2A";}

    @Override @Transactional
    public ExecutionAdapterResult submit(ExecutionAdapterRegistration adapter,ExecutionAdapterCommand command){
        if(!"READ".equalsIgnoreCase(command.operation()))return new ExecutionAdapterResult(false,"REJECTED",null,List.of("A2A_STAGE7_READ_ONLY"),OffsetDateTime.now());
        Link link=link(command.tenantId(),adapter.providerId());if(link==null)return new ExecutionAdapterResult(false,"REJECTED",null,List.of("A2A_PROVIDER_LINK_NOT_APPROVED"),OffsetDateTime.now());
        A2AVersionPolicyService.Evaluation versionEvaluation;
        try{assurance.requireReadAllowed(command.tenantId(),link.peerId(),link.interfaceId());versionEvaluation=versions.requireAllowed(command.tenantId(),link.peerId(),link.interfaceId(),link.protocolVersion());}catch(IllegalArgumentException ex){return new ExecutionAdapterResult(false,"REJECTED",null,List.of(ex.getMessage()),OffsetDateTime.now());}
        A2AExternalF0SecurityService.SecuritySnapshot securitySnapshot;
        try{securitySnapshot=security.snapshotForInterface(command.tenantId(),link.interfaceId());}catch(IllegalArgumentException ex){return new ExecutionAdapterResult(false,"REJECTED",null,List.of(ex.getMessage()),OffsetDateTime.now());}
        String binding=required(command.input().get("bindingId"),"bindingId"),auth=required(command.input().get("authorizationDecisionId"),"authorizationDecisionId"),routing=required(command.input().get("routingDecisionId"),"routingDecisionId");
        String contextType=String.valueOf(command.input().getOrDefault("executionContextType","DELEGATION"));
        String delegation="PLAN_STEP".equals(contextType)?null:required(command.input().get("delegationId"),"delegationId");
        String planRunId="PLAN_STEP".equals(contextType)?required(command.input().get("planRunId"),"planRunId"):null;
        String planStepId="PLAN_STEP".equals(contextType)?required(command.input().get("planStepId"),"planStepId"):null;
        String planAttemptId="PLAN_STEP".equals(contextType)?required(command.input().get("planAttemptId"),"planAttemptId"):null;
        AssignmentDecisionResult assignment=tasks.assignGovernedExternalProvider(new GovernedExternalProviderAssignmentRequest(command.localTaskId(),"REMOTE_A2A_AGENT","REMOTE_A2A_AGENT",adapter.providerId(),binding,auth,routing,command.executionResolutionId(),link.interfaceId(),null,null,"Stage 7 remote A2A READ provider selected server-side"));
        if(assignment.assignmentId()==null)return new ExecutionAdapterResult(false,"REJECTED",null,List.of("A2A_EXECUTION_ASSIGNMENT_NOT_CREATED"),OffsetDateTime.now());
        String executionId="a2a-read-"+UUID.randomUUID(),messageId="a2a-msg-"+UUID.randomUUID();
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("capabilityCode",command.capabilityCode());payload.put("operation",command.operation());payload.put("input",command.input());
        Map<String,Object> message=new LinkedHashMap<>();message.put("messageId",messageId);message.put("role","ROLE_USER");message.put("parts",List.of(Map.of("text",write(payload))));
        Map<String,Object> request=new LinkedHashMap<>();request.put("message",message);if(link.interfaceTenant()!=null&&!link.interfaceTenant().isBlank())request.put("tenant",link.interfaceTenant());request.put("configuration",Map.of("returnImmediately",Boolean.TRUE));
        jdbc.update("""
          insert into a2a_remote_read_executions(tenant_id,execution_id,delegation_id,execution_context_type,plan_run_id,plan_step_id,plan_attempt_id,task_id,assignment_id,provider_id,peer_id,interface_id,endpoint_url,interface_tenant,selected_protocol_version,version_policy_id,version_policy_decision,credential_binding_id,outbound_destination_policy_id,residency_decision_id,request_message_id,request_json,status,created_at,updated_at)
          values(:tenant,:execution,:delegation,:contextType,:planRun,:planStep,:planAttempt,:task,:assignment,:provider,:peer,:interface,:url,:interfaceTenant,:protocolVersion,:versionPolicyId,:versionDecision,:credentialBinding,:outboundPolicy,:residencyDecision,:message,cast(:request as jsonb),'QUEUED',:now,:now)
          """,new MapSqlParameterSource("tenant",command.tenantId()).addValue("execution",executionId).addValue("delegation",delegation).addValue("contextType",contextType).addValue("planRun",planRunId).addValue("planStep",planStepId).addValue("planAttempt",planAttemptId).addValue("task",command.localTaskId()).addValue("assignment",assignment.assignmentId()).addValue("provider",adapter.providerId()).addValue("peer",link.peerId()).addValue("interface",link.interfaceId()).addValue("url",link.url()).addValue("interfaceTenant",link.interfaceTenant()).addValue("protocolVersion",link.protocolVersion()).addValue("versionPolicyId",versionEvaluation.policyId()).addValue("versionDecision",versionEvaluation.decision()).addValue("credentialBinding",securitySnapshot.credentialBindingId()).addValue("outboundPolicy",securitySnapshot.outboundDestinationPolicyId()).addValue("residencyDecision",securitySnapshot.residencyDecisionId()).addValue("message",messageId).addValue("request",write(request)).addValue("now",OffsetDateTime.now()));
        return new ExecutionAdapterResult(true,"QUEUED",executionId,List.of("REMOTE_A2A_READ_QUEUED_NO_AGENT_DISPATCH"),OffsetDateTime.now());
    }
    private Link link(String tenant,String provider){try{return jdbc.queryForObject("""
      select l.peer_id,l.interface_id,i.url,i.interface_tenant,i.protocol_version,i.streaming_supported,i.push_notifications_supported
        from a2a_peer_provider_links l join a2a_peer_registrations p on p.tenant_id=l.tenant_id and p.peer_id=l.peer_id
        join a2a_peer_interfaces i on i.tenant_id=l.tenant_id and i.interface_id=l.interface_id
       where l.tenant_id=:tenant and l.provider_id=:provider and l.status='ACTIVE' and p.status='ACTIVE'
         and i.status='APPROVED' and i.protocol_binding='HTTP+JSON'
         and a2a_interface_current_contract_eligible(i.tenant_id,i.interface_id)
      """,new MapSqlParameterSource("tenant",tenant).addValue("provider",provider),(rs,n)->new Link(rs.getString("peer_id"),rs.getString("interface_id"),rs.getString("url"),rs.getString("interface_tenant"),rs.getString("protocol_version"),rs.getBoolean("streaming_supported"),rs.getBoolean("push_notifications_supported")));}catch(EmptyResultDataAccessException ex){return null;}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalStateException("A2A request JSON failed",ex);}}
    private static String required(Object v,String f){if(v==null||String.valueOf(v).isBlank())throw new IllegalArgumentException(f+" is required");return String.valueOf(v);}
    private record Link(String peerId,String interfaceId,String url,String interfaceTenant,String protocolVersion,boolean streamingSupported,boolean pushSupported){}
}
