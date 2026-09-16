package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** P4RA-E enforcement guard shared by REST, internal-port and streaming adapters. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class DefaultResourceAccessEnforcementGuard implements ResourceAccessEnforcementPort {
    private final ResourceAuthorizationPort authorization;
    private final ResourceEnforcementContextPort contexts;
    private final ResourceAccessEnforcementMode enforcementMode;

    public DefaultResourceAccessEnforcementGuard(
            ResourceAuthorizationPort authorization,
            ResourceEnforcementContextPort contexts,
            @Value("${resource-access.enforcement-mode:OFF}") ResourceAccessEnforcementMode enforcementMode) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.enforcementMode = enforcementMode == null ? ResourceAccessEnforcementMode.OFF : enforcementMode;
    }

    @Override
    public AuthorizationDecision assess(ResourceEnforcementCommand command) {
        Objects.requireNonNull(command, "command");
        ResourceEnforcementContext context = contexts.current();
        var authentication = context.authentication();
        AuthorizationRequest request = new AuthorizationRequest(
                authentication.principal(), authentication, authentication.activeTenant(), command.action(),
                command.resourceRef(), command.requestedVisibility(), command.requestChannel(), command.purpose(),
                command.operationPhase(), "", context.correlationId(), command.presentedEpoch(),
                command.trustedFlowContext());
        return authorization.evaluate(request);
    }

    @Override
    public AuthorizationDecision authorize(ResourceEnforcementCommand command) {
        AuthorizationDecision decision = assess(command);
        if (mustEnforce(command.action())
                && (decision.mode() != AuthorizationDecisionMode.FORMAL || decision.effect() != DecisionEffect.ALLOW)) {
            throw new ResourceAccessDeniedException(decision);
        }
        return decision;
    }

    private boolean mustEnforce(ResourceAction action) {
        return switch (enforcementMode) {
            case OFF, SHADOW -> false;
            case READ_ENFORCE -> !action.sideEffecting();
            case WRITE_ENFORCE -> action.sideEffecting();
            case FULL_ENFORCE -> true;
        };
    }
}
