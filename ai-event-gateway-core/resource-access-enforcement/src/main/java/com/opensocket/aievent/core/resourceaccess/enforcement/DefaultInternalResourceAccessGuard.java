package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Applies the same formal policy to trusted Internal Port calls; no implicit system-admin bypass. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class DefaultInternalResourceAccessGuard implements InternalResourceAccessGuardPort {
    private final ResourceAccessEnforcementPort enforcement;
    public DefaultInternalResourceAccessGuard(ResourceAccessEnforcementPort enforcement) {
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    }
    @Override
    public AuthorizationDecision authorize(ResourceAction action, ResourceRef resourceRef,
            VisibilityLevel requestedVisibility, String purpose, OperationPhase phase,
            SecurityEpoch presentedEpoch, Map<String, String> trustedFlowContext) {
        return enforcement.authorize(new ResourceEnforcementCommand(action, resourceRef, requestedVisibility,
                RequestChannel.INTERNAL_PORT, purpose, phase, presentedEpoch, trustedFlowContext));
    }
}
