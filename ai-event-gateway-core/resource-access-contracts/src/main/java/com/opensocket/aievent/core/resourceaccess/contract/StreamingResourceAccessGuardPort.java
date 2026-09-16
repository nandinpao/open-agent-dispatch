package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Map;

/** WebSocket/SSE start and chunk-boundary authorization guard. */
public interface StreamingResourceAccessGuardPort {
    AuthorizationDecision authorizeStart(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, Map<String, String> trustedFlowContext);
    AuthorizationDecision authorizeChunk(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, SecurityEpoch presentedEpoch,
            Map<String, String> trustedFlowContext);
}
