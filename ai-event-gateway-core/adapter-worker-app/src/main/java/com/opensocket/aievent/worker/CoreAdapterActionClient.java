package com.opensocket.aievent.worker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.service.ServiceContractVersions;
import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.service.adapter.AdapterWorkerCompletionRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerFailureRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerHeartbeatRequest;
import com.opensocket.aievent.worker.configuration.AdapterWorkerRuntimeConfigurationView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

/** Observable HTTP client for the external adapter worker's Core contract. */
@Service
public class CoreAdapterActionClient {
    private final AdapterWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final AdapterWorkerRuntimeConfigurationView runtimeConfiguration;
    private final RestClient compatibilityRestClient;
    private volatile ClientBundle clientBundle;

    @Autowired
    public CoreAdapterActionClient(AdapterWorkerProperties properties,ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,AdapterWorkerRuntimeConfigurationView runtimeConfiguration){
        this.properties=properties;this.objectMapper=objectMapper;this.restClientBuilder=restClientBuilder;
        this.runtimeConfiguration=runtimeConfiguration;this.compatibilityRestClient=null;
    }

    /** Compatibility/test constructor retained for focused tests that provide their own RestClient. */
    public CoreAdapterActionClient(AdapterWorkerProperties properties,ObjectMapper objectMapper,RestClient restClient){
        this.properties=properties;this.objectMapper=objectMapper;this.compatibilityRestClient=restClient;
        this.restClientBuilder=null;this.runtimeConfiguration=null;
    }

    public Optional<AdapterWorkItem> claim(String adapterType){
        String claimPath="/internal/adapter-actions/claim?adapterType="+encode(adapterType)+"&workerId="+encode(properties.getWorkerId())+"&leaseSeconds="+leaseSeconds();
        try{CoreResponse response=exchange(HttpMethod.POST,claimPath,null);if(response.statusCode()==204)return Optional.empty();if(!isSuccessful(response.statusCode()))throw new IllegalStateException("claim returned HTTP "+response.statusCode());return Optional.of(objectMapper.readValue(response.body(),AdapterWorkItem.class));}
        catch(Exception exception){throw new IllegalStateException("claim failed",exception);}
    }
    public void complete(AdapterWorkItem item,AdapterWorkResult result){post("/internal/adapter-actions/"+item.actionId()+"/complete",new AdapterWorkerCompletionRequest(properties.getWorkerId(),result.responseRef(),Map.of()));}
    public void fail(AdapterWorkItem item,AdapterWorkResult result){post("/internal/adapter-actions/"+item.actionId()+"/fail",new AdapterWorkerFailureRequest(properties.getWorkerId(),result.error(),result.retryable()));}
    public void heartbeat(AdapterWorkItem item){post("/internal/adapter-actions/"+item.actionId()+"/heartbeat",new AdapterWorkerHeartbeatRequest(properties.getWorkerId(),leaseSeconds()));}
    String workerId(){return properties.getWorkerId();}

    private long leaseSeconds(){return runtimeConfiguration==null?properties.getLeaseSeconds():runtimeConfiguration.leaseSeconds();}
    private Duration requestTimeout(){return runtimeConfiguration==null?properties.getRequestTimeout():runtimeConfiguration.requestTimeout();}
    private void post(String path,Object body){try{CoreResponse response=exchange(HttpMethod.POST,path,objectMapper.writeValueAsString(body));if(!isSuccessful(response.statusCode()))throw new IllegalStateException(path+" returned HTTP "+response.statusCode());}catch(Exception exception){throw new IllegalStateException("worker callback failed",exception);}}
    private CoreResponse exchange(HttpMethod method,String path,String body){
        RestClient client=restClient();
        RestClient.RequestBodySpec request=client.method(method).uri(path).header(ServiceContractVersions.CONTRACT_HEADER,ServiceContractVersions.ADAPTER_WORKER_V1);
        if(!properties.getToken().isBlank())request.header(properties.getTokenHeader(),properties.getToken());
        RestClient.RequestHeadersSpec<?> exchange=body==null?request:request.header("Content-Type","application/json").body(body);
        return exchange.exchange((clientRequest,clientResponse)->new CoreResponse(clientResponse.getStatusCode().value(),StreamUtils.copyToString(clientResponse.getBody(),StandardCharsets.UTF_8)));
    }
    private RestClient restClient(){
        if(compatibilityRestClient!=null)return compatibilityRestClient;
        Duration timeout=requestTimeout();ClientBundle current=clientBundle;if(current!=null&&current.timeout.equals(timeout))return current.client;
        synchronized(this){current=clientBundle;if(current!=null&&current.timeout.equals(timeout))return current.client;HttpClient httpClient=HttpClient.newBuilder().connectTimeout(timeout).build();JdkClientHttpRequestFactory requestFactory=new JdkClientHttpRequestFactory(httpClient);requestFactory.setReadTimeout(timeout);RestClient client=restClientBuilder.clone().baseUrl(properties.getCoreBaseUrl()).requestFactory(requestFactory).build();clientBundle=new ClientBundle(timeout,client);return client;}
    }
    private boolean isSuccessful(int statusCode){return statusCode>=200&&statusCode<300;}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private record CoreResponse(int statusCode,String body){}
    private record ClientBundle(Duration timeout,RestClient client){}
}
