package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Stage 6 durable MCP READ worker. */
@Component
@ConditionalOnProperty(name="opendispatch.mcp-read.enabled",havingValue="true",matchIfMissing=true)
public class McpReadExecutionWorker {
    private final JdbcTemplate jdbc; private final McpReadExecutionQueueService queue; private final ProviderNeutralExecutionCompletionRouter completion; private final CapabilityRemoteExecutionSafetyService safety;
    private final ObjectMapper json; private final HttpClient http; private final OutboundDestinationValidator destinations; private final String workerId; private final int batchSize;
    public McpReadExecutionWorker(JdbcTemplate jdbc,McpReadExecutionQueueService queue,ProviderNeutralExecutionCompletionRouter completion,CapabilityRemoteExecutionSafetyService safety,ObjectMapper json,OutboundDestinationValidator destinations,
            @Value("${opendispatch.mcp-read.worker-id:mcp-read-worker}") String workerId,@Value("${opendispatch.mcp-read.batch-size:20}") int batchSize){
        this.jdbc=jdbc;this.queue=queue;this.completion=completion;this.safety=safety;this.json=json;this.destinations=destinations;this.workerId=workerId;this.batchSize=Math.max(1,Math.min(batchSize,50));this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();}
    @Scheduled(fixedDelayString="${opendispatch.mcp-read.poll-ms:2000}") public void run(){for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){for(McpReadExecutionQueueService.Item item:queue.claimDue(tenant,workerId,batchSize))process(tenant,item);}}
    private void process(String tenant,McpReadExecutionQueueService.Item item){try{
        safety.requireAllowed(tenant,item.assignmentId());
        URI endpoint=destinations.requireAllowed(item.endpointUrl(),OutboundDestinationPolicy.registeredEnterpriseService(),"MCP_RUNTIME");
        HttpRequest req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Accept","application/json, text/event-stream").header("MCP-Protocol-Version","2026-07-28").POST(HttpRequest.BodyPublishers.ofString(item.requestJson())).build();
        HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("MCP_HTTP_"+response.statusCode());
        String contentType=response.headers().firstValue("Content-Type").orElse("").toLowerCase(); if(contentType.contains("text/event-stream"))throw new IllegalStateException("MCP_STREAMING_NOT_SUPPORTED_STAGE6");
        Map<String,Object> body=read(response.body()); boolean isError=false;Object result=body.get("result");if(result instanceof Map<?,?> m){Object v=m.get("isError");isError=Boolean.TRUE.equals(v);}
        queue.success(tenant,item.executionId(),response.statusCode(),response.body());
        completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"MCP_TOOL",item.providerId(),item.executionId(),!isError,body,isError?"MCP_TOOL_ERROR":null,isError?"MCP tool returned isError=true":null);
    }catch(Exception ex){queue.failure(tenant,item.executionId(),"MCP_EXECUTION_FAILED",ex.getMessage(),item.attempt(),item.maxAttempts());if(item.attempt()>=item.maxAttempts())completion.complete(tenant,item.executionContextType(),item.delegationId(),item.planRunId(),item.planStepId(),item.taskId(),"MCP_TOOL",item.providerId(),item.executionId(),false,Map.of("error",safe(ex.getMessage())),"MCP_EXECUTION_FAILED",ex.getMessage());}}
    private Map<String,Object> read(String raw){try{return json.readValue(raw,new TypeReference<Map<String,Object>>(){});}catch(Exception ex){return new LinkedHashMap<>(Map.of("raw",raw));}}
    private static String safe(String v){return v==null?"":v;}
}
