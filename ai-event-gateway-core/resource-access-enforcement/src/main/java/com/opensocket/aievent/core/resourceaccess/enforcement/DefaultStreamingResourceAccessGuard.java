package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Shared WebSocket/SSE guard; chunk checks reject stale epoch decisions during a stream. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class DefaultStreamingResourceAccessGuard implements StreamingResourceAccessGuardPort {
    private final ResourceAccessEnforcementPort enforcement;
    public DefaultStreamingResourceAccessGuard(ResourceAccessEnforcementPort enforcement) {
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    }
    @Override
    public AuthorizationDecision authorizeStart(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, Map<String, String> trustedFlowContext) {
        return enforcement.authorize(new ResourceEnforcementCommand(action, resourceRef, requestedVisibility,
                RequestChannel.WEBSOCKET, purpose, OperationPhase.START, SecurityEpoch.ZERO, trustedFlowContext));
    }
    @Override
    public AuthorizationDecision authorizeChunk(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, SecurityEpoch presentedEpoch,
            Map<String, String> trustedFlowContext) {
        return enforcement.authorize(new ResourceEnforcementCommand(action, resourceRef, requestedVisibility,
                RequestChannel.WEBSOCKET, purpose, OperationPhase.BATCH_BOUNDARY,
                presentedEpoch, trustedFlowContext));
    }
}
