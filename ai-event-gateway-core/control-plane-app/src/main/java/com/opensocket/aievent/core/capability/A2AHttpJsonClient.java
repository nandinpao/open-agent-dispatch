package com.opensocket.aievent.core.capability;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * External A2A HTTP+JSON client backed by the PC-S4 dedicated secure transport.
 * Protocol version, credential binding and outbound policy are immutable-by-execution snapshots.
 */
@Component
public class A2AHttpJsonClient {
    private final ObjectMapper json;
    private final A2ASecureHttpTransport transport;

    public A2AHttpJsonClient(ObjectMapper json, A2ASecureHttpTransport transport) {
        this.json = json;
        this.transport = transport;
    }

    public Response sendMessage(String baseUrl,String interfaceTenant,String protocolVersion,Map<String,Object> body,A2AExternalF0SecurityService.RuntimeSecurity security){return post(join(baseUrl,"/message:send"),protocolVersion,body,security);}
    public Response getTask(String baseUrl,String interfaceTenant,String protocolVersion,String taskId,A2AExternalF0SecurityService.RuntimeSecurity security){
        String url=join(baseUrl,"/tasks/"+segment(taskId)); if(interfaceTenant!=null&&!interfaceTenant.isBlank())url += "?tenant="+query(interfaceTenant)+"&historyLength=0"; else url += "?historyLength=0";
        return get(url,protocolVersion,security);
    }
    public Response cancelTask(String baseUrl,String interfaceTenant,String protocolVersion,String taskId,A2AExternalF0SecurityService.RuntimeSecurity security){
        Map<String,Object> body=interfaceTenant==null||interfaceTenant.isBlank()?Map.of():Map.of("tenant",interfaceTenant);
        return post(join(baseUrl,"/tasks/"+segment(taskId)+":cancel"),protocolVersion,body,security);
    }
    public Response createPushConfig(String baseUrl,String interfaceTenant,String protocolVersion,String taskId,String callbackUrl,String token,A2AExternalF0SecurityService.RuntimeSecurity security){
        Map<String,Object> body=new LinkedHashMap<>();
        if(interfaceTenant!=null&&!interfaceTenant.isBlank())body.put("tenant",interfaceTenant);
        body.put("url",callbackUrl); body.put("token",token); body.put("authentication",Map.of("scheme","Bearer","credentials",token));
        return post(join(baseUrl,"/tasks/"+segment(taskId)+"/pushNotificationConfigs"),protocolVersion,body,security);
    }
    public StreamResponse subscribe(String baseUrl,String interfaceTenant,String protocolVersion,String taskId,A2AExternalF0SecurityService.RuntimeSecurity security){
        Map<String,Object> body=interfaceTenant==null||interfaceTenant.isBlank()?Map.of():Map.of("tenant",interfaceTenant);
        byte[] encoded=write(body).getBytes(StandardCharsets.UTF_8);
        Map<String,String> headers=headers(protocolVersion,security,true);
        A2ASecureHttpTransport.SseResponse response=transport.subscribe(join(baseUrl,"/tasks/"+segment(taskId)+":subscribe"),headers,encoded,security);
        if(response.statusCode()<200||response.statusCode()>=300){
            String raw=new String(response.errorBody(),StandardCharsets.UTF_8);
            return new StreamResponse(response.statusCode(),List.of(),raw,readOrRaw(raw));
        }
        java.util.ArrayList<Map<String,Object>> events=new java.util.ArrayList<>();
        for(String raw:response.events()){
            try{events.add(read(raw));}
            catch(Exception ex){throw new IllegalStateException("A2A_SSE_EVENT_PARSE_FAILED",ex);}
        }
        return new StreamResponse(response.statusCode(),List.copyOf(events),"",Map.of());
    }

    private Response get(String url,String protocolVersion,A2AExternalF0SecurityService.RuntimeSecurity security){
        A2ASecureHttpTransport.ExchangeResponse r=transport.exchange("GET",url,headers(protocolVersion,security,false),new byte[0],security);
        return response(r);
    }
    private Response post(String url,String protocolVersion,Map<String,Object> body,A2AExternalF0SecurityService.RuntimeSecurity security){
        byte[] encoded=write(body).getBytes(StandardCharsets.UTF_8);
        A2ASecureHttpTransport.ExchangeResponse r=transport.exchange("POST",url,headers(protocolVersion,security,true),encoded,security);
        return response(r);
    }
    private Response response(A2ASecureHttpTransport.ExchangeResponse r){
        String raw=new String(r.body(),StandardCharsets.UTF_8);
        return new Response(r.statusCode(),raw,readOrRaw(raw),r.headers().getOrDefault("content-type",""));
    }
    private Map<String,String> headers(String protocolVersion,A2AExternalF0SecurityService.RuntimeSecurity security,boolean contentType){
        if(security==null)throw new IllegalArgumentException("A2A_EXECUTION_SECURITY_SNAPSHOT_REQUIRED");
        String version=requiredVersion(protocolVersion);
        A2APeerCredentialSecretResolver.ResolvedCredential credential=security.credential();
        Map<String,String> headers=new LinkedHashMap<>();
        headers.put("A2A-Version",version);
        headers.put("Accept","application/a2a+json, text/event-stream");
        if(contentType)headers.put("Content-Type","application/a2a+json");
        headers.put(credential.headerName(),credential.headerValue());
        return headers;
    }
    private Map<String,Object> readOrRaw(String raw){try{return read(raw);}catch(Exception ex){return Map.of("raw",raw==null?"":raw);}}
    private Map<String,Object> read(String raw)throws Exception{return json.readValue(raw,new TypeReference<Map<String,Object>>(){});}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalStateException("A2A_JSON_SERIALIZATION_FAILED",ex);}}

    /** Joins endpoint path without allowing a base URL query to swallow the A2A operation path. */
    private static String join(String base,String path){
        if(base==null||base.isBlank())throw new IllegalArgumentException("A2A_ENDPOINT_REQUIRED");
        URI uri;
        try{uri=URI.create(base.trim());}catch(Exception ex){throw new IllegalArgumentException("A2A_ENDPOINT_INVALID",ex);}
        if(uri.getRawQuery()!=null||uri.getFragment()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException("A2A_ENDPOINT_BASE_COMPONENT_NOT_ALLOWED");
        String p=uri.getRawPath()==null?"":uri.getRawPath();while(p.endsWith("/"))p=p.substring(0,p.length()-1);
        String host=uri.getHost();if(host==null||host.isBlank())throw new IllegalArgumentException("A2A_ENDPOINT_INVALID");
        String authority=(host.contains(":")?"["+host+"]":host)+(uri.getPort()>0?":"+uri.getPort():"");
        try{return URI.create(uri.getScheme()+"://"+authority+p+path).toASCIIString();}
        catch(Exception ex){throw new IllegalArgumentException("A2A_ENDPOINT_INVALID",ex);}
    }
    private static String segment(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8).replace("+","%20");}
    private static String query(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String requiredVersion(String v){if(v==null||!v.matches("[0-9]{1,6}\\.[0-9]{1,6}"))throw new IllegalArgumentException("A2A_PROTOCOL_VERSION_INVALID");return v;}
    public record Response(int statusCode,String rawBody,Map<String,Object> body,String contentType){public boolean ok(){return statusCode>=200&&statusCode<300;}}
    public record StreamResponse(int statusCode,List<Map<String,Object>> events,String rawErrorBody,Map<String,Object> errorBody){}
}
