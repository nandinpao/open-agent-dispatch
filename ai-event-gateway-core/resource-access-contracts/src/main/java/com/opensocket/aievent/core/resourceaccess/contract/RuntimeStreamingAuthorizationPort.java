package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Map;
public interface RuntimeStreamingAuthorizationPort {
 StreamingAuthorizationSession start(ResourceAction action,ResourceRef resourceRef,VisibilityLevel visibility,String purpose,Map<String,String> trustedFlowContext);
 RuntimeAuthorizationLease checkpoint(RuntimeAuthorizationCheckpoint checkpoint);
 RuntimeAuthorizationLease complete(String tenantId,String leaseId,String correlationId);
 default RuntimeAuthorizationLease complete(String tenantId,String leaseId){return complete(tenantId,leaseId,"stream-complete");}
}
