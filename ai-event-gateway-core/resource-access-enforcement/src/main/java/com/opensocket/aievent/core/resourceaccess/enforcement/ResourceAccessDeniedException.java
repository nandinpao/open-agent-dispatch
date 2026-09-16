package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;

/** Safe enforcement exception. API adapters may expose decisionId and reason codes, never hidden resource data. */
public final class ResourceAccessDeniedException extends org.springframework.security.access.AccessDeniedException {
    private final AuthorizationDecision decision;
    public ResourceAccessDeniedException(AuthorizationDecision decision) {
        super("RESOURCE_ACCESS_DENIED:" + decision.decisionId());
        this.decision = decision;
    }
    public AuthorizationDecision decision() { return decision; }
}
