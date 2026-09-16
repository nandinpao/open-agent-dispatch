package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.DispatchExecutionLifecycleObserver;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.GatewayDispatchResult;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/** Records the actual network boundary for V206 DispatchIntent without initiating or retrying I/O. */
@Component
public class A0R7DispatchIntentLifecycleObserver implements DispatchExecutionLifecycleObserver {
    private final ExecutionDispatchIntentStore intents;
    public A0R7DispatchIntentLifecycleObserver(ExecutionDispatchIntentStore intents){this.intents=intents;}
    @Override public int order(){return 50;}

    @Override
    public void beforeNetwork(DispatchRequest request, OffsetDateTime at){
        // C0-A2: SEND_STARTED is an authority transition and is therefore committed by
        // A0R7AtomicPreSendAdmission in the final short transaction immediately before I/O.
        // Lifecycle observers are deliberately non-authoritative preparation/observation only.
    }

    @Override
    public void afterNetwork(DispatchRequest request, GatewayDispatchResult result, OffsetDateTime at){
        if(request==null||blank(request.getTenantId())||blank(request.getDispatchRequestId())||result==null)return;
        boolean unknown=!result.success()&&uncertain(result);
        intents.markNetworkResultByDispatchRequest(request.getTenantId(),request.getDispatchRequestId(),result.success(),unknown,result.gatewayStatus(),result.message());
    }

    private boolean uncertain(GatewayDispatchResult r){String s=(r.gatewayStatus()==null?"":r.gatewayStatus()).toUpperCase();return r.httpStatus()==0||s.contains("TIMEOUT")||s.contains("EXCEPTION")||s.contains("NULL_GATEWAY")||s.contains("RESPONSE_LOST")||s.contains("UNKNOWN");}
    private boolean blank(String v){return v==null||v.isBlank();}
}
