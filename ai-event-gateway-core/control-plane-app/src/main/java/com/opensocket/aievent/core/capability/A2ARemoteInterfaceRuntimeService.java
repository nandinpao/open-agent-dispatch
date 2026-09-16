package com.opensocket.aievent.core.capability;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped lookup of the selected Stage 7 A2A interface. */
@Service
public class A2ARemoteInterfaceRuntimeService {
    private final NamedParameterJdbcTemplate jdbc; private final A2ATrustAssurancePolicyService assurance; private final A2AVersionPolicyService versions; public A2ARemoteInterfaceRuntimeService(NamedParameterJdbcTemplate jdbc,A2ATrustAssurancePolicyService assurance,A2AVersionPolicyService versions){this.jdbc=jdbc;this.assurance=assurance;this.versions=versions;}
    @Transactional(readOnly=true) public InterfaceRuntime find(String tenant,String interfaceId){bind(tenant);try{InterfaceRuntime runtime=jdbc.queryForObject("""
      select i.interface_id,i.peer_id,i.url,i.interface_tenant,i.protocol_version,i.streaming_supported,i.push_notifications_supported
        from a2a_peer_interfaces i join a2a_peer_registrations p on p.tenant_id=i.tenant_id and p.peer_id=i.peer_id
       where i.tenant_id=:tenant and i.interface_id=:interface and i.status='APPROVED'
         and p.status='ACTIVE' and i.protocol_binding='HTTP+JSON'
         and a2a_interface_current_contract_eligible(i.tenant_id,i.interface_id)
      """,new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId),(rs,n)->new InterfaceRuntime(rs.getString("interface_id"),rs.getString("peer_id"),rs.getString("url"),rs.getString("interface_tenant"),rs.getString("protocol_version"),rs.getBoolean("streaming_supported"),rs.getBoolean("push_notifications_supported")));assurance.requireReadAllowed(tenant,runtime.peerId(),runtime.interfaceId());versions.requireAllowed(tenant,runtime.peerId(),runtime.interfaceId(),runtime.protocolVersion());return runtime;}catch(EmptyResultDataAccessException|IllegalArgumentException ex){return null;}}
    @Transactional(readOnly=true) public ExecutionRuntime execution(String tenant,String executionId){bind(tenant);try{return jdbc.queryForObject("""
      select execution_id,execution_context_type,delegation_id,plan_run_id,plan_step_id,plan_attempt_id,task_id,assignment_id,provider_id,peer_id,interface_id,endpoint_url,interface_tenant,selected_protocol_version,version_policy_id,version_policy_decision,credential_binding_id,outbound_destination_policy_id,remote_task_id,remote_state,status,tracking_id
        from a2a_remote_read_executions where tenant_id=:tenant and execution_id=:execution
      """,new MapSqlParameterSource("tenant",tenant).addValue("execution",executionId),(rs,n)->new ExecutionRuntime(rs.getString("execution_id"),rs.getString("execution_context_type"),rs.getString("delegation_id"),rs.getString("plan_run_id"),rs.getString("plan_step_id"),rs.getString("plan_attempt_id"),rs.getString("task_id"),rs.getString("assignment_id"),rs.getString("provider_id"),rs.getString("peer_id"),rs.getString("interface_id"),rs.getString("endpoint_url"),rs.getString("interface_tenant"),rs.getString("selected_protocol_version"),rs.getString("version_policy_id"),rs.getString("version_policy_decision"),rs.getString("credential_binding_id"),rs.getString("outbound_destination_policy_id"),rs.getString("remote_task_id"),rs.getString("remote_state"),rs.getString("status"),rs.getString("tracking_id")));}catch(EmptyResultDataAccessException ex){return null;}}
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"a2a-runtime-query");}
    public record InterfaceRuntime(String interfaceId,String peerId,String url,String interfaceTenant,String protocolVersion,boolean streamingSupported,boolean pushSupported){}
    public record ExecutionRuntime(String executionId,String executionContextType,String delegationId,String planRunId,String planStepId,String planAttemptId,String taskId,String assignmentId,String providerId,String peerId,String interfaceId,String endpointUrl,String interfaceTenant,String protocolVersion,String versionPolicyId,String versionPolicyDecision,String credentialBindingId,String outboundDestinationPolicyId,String remoteTaskId,String remoteState,String status,String trackingId){}
}
