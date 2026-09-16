package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** PC-S4 PUSH setup with opaque pre-tenant callback routing. Raw callback tokens are never persisted. */
@Service
public class A2APushNotificationConfigurationService {
    private final A2AHttpJsonClient client; private final A2ARemoteTaskTrackingService tracking; private final A2ARemoteInterfaceRuntimeService interfaces; private final A2APeerErrorMappingService errors; private final A2AExternalF0SecurityService security; private final A2APushCallbackRouteService routes;
    public A2APushNotificationConfigurationService(A2AHttpJsonClient client,A2ARemoteTaskTrackingService tracking,A2ARemoteInterfaceRuntimeService interfaces,A2APeerErrorMappingService errors,A2AExternalF0SecurityService security,A2APushCallbackRouteService routes){this.client=client;this.tracking=tracking;this.interfaces=interfaces;this.errors=errors;this.security=security;this.routes=routes;}
    public boolean configure(String tenant,A2ARemoteTaskTrackingService.RemoteTrackingLease tr){
        if(tracking.pushBaseUrl().isBlank())return false;A2ARemoteInterfaceRuntimeService.InterfaceRuntime iface=interfaces.find(tenant,tr.interfaceId());A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution=interfaces.execution(tenant,tr.executionId());if(iface==null||execution==null||!iface.pushSupported())return false;
        String token="a2a-push-"+UUID.randomUUID()+UUID.randomUUID().toString().replace("-","");String handle=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");String callback=trimSlash(tracking.pushBaseUrl())+"/internal/a2a/push/"+handle;String tokenHash=sha256(token);routes.register(tenant,tr.trackingId(),handle,tokenHash);
        try{A2AExternalF0SecurityService.RuntimeSecurity runtimeSecurity=security.runtimeSecurityForExecution(tenant,tr.executionId());A2AHttpJsonClient.Response response=client.createPushConfig(execution.endpointUrl(),execution.interfaceTenant(),execution.protocolVersion(),tr.remoteTaskId(),callback,token,runtimeSecurity);if(!response.ok()){routes.revoke(handle);errors.resolveAndRecord(tenant,tr.executionId(),tr.trackingId(),tr.peerId(),tr.interfaceId(),"PUSH_CONFIG",response.statusCode(),response.body(),response.rawBody());return false;}Object id=response.body().get("id");String config=id==null?"remote-config-unknown":String.valueOf(id);tracking.pushConfigured(tenant,tr.trackingId(),config,tokenHash);return true;}catch(Exception ex){routes.revoke(handle);return false;}
    }
    private static String trimSlash(String v){String x=v;while(x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
