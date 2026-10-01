package com.opensocket.aievent.worker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.worker.configuration.AdapterWorkerRuntimeConfigurationView;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DefaultAdapterWorkExecutor implements AdapterWorkExecutor {
    private final AdapterWorkerProperties properties;
    private final AdapterWorkerRuntimeConfigurationView runtimeConfiguration;
    private final ObjectMapper objectMapper;
    private volatile ClientBundle clientBundle;

    public DefaultAdapterWorkExecutor(AdapterWorkerProperties properties,AdapterWorkerRuntimeConfigurationView runtimeConfiguration,ObjectMapper objectMapper){
        this.properties=properties;this.runtimeConfiguration=runtimeConfiguration;this.objectMapper=objectMapper;
    }
    @Override public boolean supports(AdapterWorkItem item){return item!=null&&"MCP".equalsIgnoreCase(item.adapterType());}
    @Override public AdapterWorkResult execute(AdapterWorkItem item){
        String endpoint=resolveEndpoint(item.adapterType());
        if(endpoint.isBlank())return properties.isMockSuccessEnabled()?AdapterWorkResult.success("mock:"+properties.getWorkerId()+":"+item.actionId()):AdapterWorkResult.failure("No external executor endpoint configured for "+item.adapterType(),true);
        Duration timeout=runtimeConfiguration.requestTimeout();
        try{HttpRequest request=HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(timeout).header("Content-Type","application/json").header("Idempotency-Key",resolveIdempotencyKey(item)).POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(item))).build();HttpResponse<String> response=httpClient(timeout).send(request,HttpResponse.BodyHandlers.ofString());int statusCode=response.statusCode();if(statusCode>=200&&statusCode<300)return AdapterWorkResult.success(item.adapterType().toLowerCase()+":"+item.actionId()+":"+statusCode);return AdapterWorkResult.failure(item.adapterType()+" endpoint returned "+statusCode+": "+response.body(),statusCode>=500||statusCode==429);}
        catch(InterruptedException exception){Thread.currentThread().interrupt();return AdapterWorkResult.failure("Adapter execution interrupted",true);}catch(IOException|RuntimeException exception){String message=exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage();return AdapterWorkResult.failure(message,true);}
    }
    private HttpClient httpClient(Duration timeout){ClientBundle current=clientBundle;if(current!=null&&current.timeout.equals(timeout))return current.client;synchronized(this){current=clientBundle;if(current!=null&&current.timeout.equals(timeout))return current.client;HttpClient client=HttpClient.newBuilder().connectTimeout(timeout).build();clientBundle=new ClientBundle(timeout,client);return client;}}
    private String resolveEndpoint(String adapterType){if("MCP".equalsIgnoreCase(adapterType))return properties.getMcpEndpointUrl();return "";}
    private String resolveIdempotencyKey(AdapterWorkItem item){return item.idempotencyKey()==null||item.idempotencyKey().isBlank()?item.actionId():item.idempotencyKey();}
    private record ClientBundle(Duration timeout,HttpClient client){}
}
