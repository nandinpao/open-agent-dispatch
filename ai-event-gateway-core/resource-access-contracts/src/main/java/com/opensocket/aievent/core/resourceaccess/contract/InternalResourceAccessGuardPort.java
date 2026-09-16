package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Map;

/** Trusted modular-monolith boundary for internal Task/A2A calls. */
public interface InternalResourceAccessGuardPort {
    AuthorizationDecision authorize(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, OperationPhase phase,
            SecurityEpoch presentedEpoch, Map<String, String> trustedFlowContext);
}
