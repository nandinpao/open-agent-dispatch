package com.opensocket.aievent.core.capability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Stage 7 initial A2A SendMessage worker. C0-B6 canonicalizes peer errors before failure handling. */
@Component
@ConditionalOnProperty(name="opendispatch.a2a-read.enabled",havingValue="true",matchIfMissing=true)
public class A2ARemoteReadExecutionWorker {
    private final JdbcTemplate jdbc;
    private final A2ARemoteReadExecutionQueueService queue;
    private final CapabilityRemoteExecutionSafetyService safety;
    private final A2AHttpJsonClient client;
    private final ObjectMapper json;
    private final ProviderNeutralExecutionCompletionRouter completion;
    private final A2ARemoteTaskTrackingService tracking;
    private final A2ARemoteInterfaceRuntimeService interfaces;
    private final A2APushNotificationConfigurationService push;
    private final A2APeerErrorMappingService errors;
    private final A2AExternalF0SecurityService security;
    private final String workerId;
    private final int batchSize;

    public A2ARemoteReadExecutionWorker(JdbcTemplate jdbc,A2ARemoteReadExecutionQueueService queue,CapabilityRemoteExecutionSafetyService safety,A2AHttpJsonClient client,ObjectMapper json,
            ProviderNeutralExecutionCompletionRouter completion,A2ARemoteTaskTrackingService tracking,A2ARemoteInterfaceRuntimeService interfaces,A2APushNotificationConfigurationService push,A2APeerErrorMappingService errors,A2AExternalF0SecurityService security,
            @Value("${opendispatch.a2a-read.worker-id:a2a-read-worker}") String workerId,@Value("${opendispatch.a2a-read.batch-size:20}") int batchSize){
        this.jdbc=jdbc;this.queue=queue;this.safety=safety;this.client=client;this.json=json;this.completion=completion;this.tracking=tracking;this.interfaces=interfaces;this.push=push;this.errors=errors;this.security=security;this.workerId=workerId;this.batchSize=Math.max(1,Math.min(batchSize,50));
    }

    @Scheduled(fixedDelayString="${opendispatch.a2a-read.poll-ms:2000}", scheduler="a2aRemoteOperationalScheduler")
    public void run(){for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){for(A2ARemoteReadExecutionQueueService.Item item:queue.claimDue(tenant,workerId,batchSize))process(tenant,item);}}

    private void process(String tenant,A2ARemoteReadExecutionQueueService.Item item){
        try{
            safety.requireAllowed(tenant,item.assignmentId());Map<String,Object> request=read(item.requestJson());
            A2AExternalF0SecurityService.RuntimeSecurity runtimeSecurity=security.runtimeSecurityForExecution(tenant,item.executionId());
            A2AHttpJsonClient.Response response=client.sendMessage(item.endpointUrl(),item.interfaceTenant(),item.protocolVersion(),request,runtimeSecurity);
            if(!response.ok()){
                A2APeerErrorMappingService.Resolution r=errors.resolveAndRecord(tenant,item.executionId(),null,item.peerId(),item.interfaceId(),"SEND_MESSAGE",response.statusCode(),response.body(),response.rawBody());
                A2ARemoteReadExecutionQueueService.FailureOutcome outcome=queue.mappedFailure(tenant,item.executionId(),r,item.attempt(),item.maxAttempts());
                if(outcome.completeNow())completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"REMOTE_A2A_AGENT",item.providerId(),item.executionId(),false,Map.of("remoteErrorCode",safe(r.remoteErrorCode()),"errorClass",r.resolvedErrorClass(),"disposition",r.disposition()),r.canonicalErrorCode(),safe(r.remoteErrorMessage()));
                return;
            }
            Map<String,Object> task=A2AProtocolObjects.task(response.body());String remoteTask=A2AProtocolObjects.id(task),state=A2AProtocolObjects.state(task),context=A2AProtocolObjects.contextId(task);
            if(remoteTask==null||remoteTask.isBlank())throw new IllegalStateException("A2A_RESPONSE_TASK_ID_REQUIRED");
            if(A2AProtocolObjects.terminal(state)){
                if(A2AProtocolObjects.success(state)){queue.success(tenant,item.executionId(),state,response.rawBody());completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"REMOTE_A2A_AGENT",item.providerId(),item.executionId(),true,response.body(),null,null);}
                else {
                    A2APeerErrorMappingService.Resolution r=errors.resolveAndRecord(tenant,item.executionId(),null,item.peerId(),item.interfaceId(),"TASK_TERMINAL",response.statusCode(),response.body(),"Remote A2A terminal state="+state);
                    queue.mappedFailure(tenant,item.executionId(),r,item.maxAttempts(),item.maxAttempts());
                    completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"REMOTE_A2A_AGENT",item.providerId(),item.executionId(),false,response.body(),r.canonicalErrorCode(),safe(r.remoteErrorMessage()));
                }
                return;
            }
            queue.waiting(tenant,item.executionId(),remoteTask,context,state,response.rawBody());A2ARemoteInterfaceRuntimeService.InterfaceRuntime iface=interfaces.find(tenant,item.interfaceId());if(iface==null)throw new IllegalStateException("A2A_INTERFACE_NOT_CURRENTLY_APPROVED");
            A2ARemoteTaskTrackingService.RemoteTrackingLease tr=tracking.start(tenant,item.executionId(),item.delegationId(),remoteTask,item.peerId(),item.interfaceId(),iface.streamingSupported(),iface.pushSupported());if(("PUSH".equals(tr.mode())||"HYBRID".equals(tr.mode()))&&!tracking.pushBaseUrl().isBlank())push.configure(tenant,tr);
        }catch(Exception ex){
            A2ARemoteReadExecutionQueueService.FailureOutcome outcome=queue.failure(tenant,item.executionId(),"A2A_SEND_FAILED",ex.getMessage(),item.attempt(),item.maxAttempts());
            if(outcome.completeNow())completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"REMOTE_A2A_AGENT",item.providerId(),item.executionId(),false,Map.of("error",safe(ex.getMessage())),"A2A_SEND_FAILED",ex.getMessage());
        }
    }

    private Map<String,Object> read(String raw){try{return json.readValue(raw,new TypeReference<Map<String,Object>>(){});}catch(Exception ex){return new LinkedHashMap<>();}}
    private static String safe(String v){return v==null?"":v;}
}
