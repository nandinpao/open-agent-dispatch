package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.stereotype.Component;
/** Lease-backed WebSocket/SSE authorization. Every chunk boundary checks fencing and composite epochs. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="runtime-lease-enabled",havingValue="true")
public final class DefaultRuntimeStreamingAuthorizationService implements RuntimeStreamingAuthorizationPort {
 private final ResourceAccessEnforcementPort enforcement;private final ResourceEnforcementContextPort contexts;private final RuntimeAuthorizationLeasePort leases;
 public DefaultRuntimeStreamingAuthorizationService(ResourceAccessEnforcementPort enforcement,ResourceEnforcementContextPort contexts,RuntimeAuthorizationLeasePort leases){this.enforcement=Objects.requireNonNull(enforcement);this.contexts=Objects.requireNonNull(contexts);this.leases=Objects.requireNonNull(leases);}
 @Override public StreamingAuthorizationSession start(ResourceAction action,ResourceRef ref,VisibilityLevel visibility,String purpose,Map<String,String> flow){AuthorizationDecision decision=enforcement.authorize(new ResourceEnforcementCommand(action,ref,visibility,RequestChannel.WEBSOCKET,purpose,OperationPhase.START,SecurityEpoch.ZERO,flow));if(decision.mode()!=AuthorizationDecisionMode.FORMAL||decision.effect()!=DecisionEffect.ALLOW||decision.shadowOnly())throw new IllegalStateException("STREAM_AUTHORIZATION_NOT_EXECUTABLE");ResourceEnforcementContext context=contexts.current();AuthorizationRequest request=new AuthorizationRequest(context.authentication().principal(),context.authentication(),context.authentication().activeTenant(),action,ref,visibility,RequestChannel.WEBSOCKET,purpose,OperationPhase.START,"",context.correlationId(),decision.securityEpoch(),flow);return new StreamingAuthorizationSession(decision,leases.issue(request,decision));}
 @Override public RuntimeAuthorizationLease checkpoint(RuntimeAuthorizationCheckpoint checkpoint){return leases.check(checkpoint);}
 @Override public RuntimeAuthorizationLease complete(String tenantId,String leaseId,String correlationId){return leases.complete(tenantId,leaseId,correlationId);}
}
