package com.opensocket.aievent.core.iam.api.security;

import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import java.util.Objects;

public final class IamAuthorizationException extends RuntimeException {
    private final AuthorizationDecision decision;
    private final String requiredPermission;
    public IamAuthorizationException(AuthorizationDecision decision, String requiredPermission) {
        super(decision.reasonCode());
        this.decision = Objects.requireNonNull(decision, "decision");
        this.requiredPermission = requiredPermission == null ? "" : requiredPermission.trim();
    }
    public AuthorizationDecision decision() { return decision; }
    public String requiredPermission() { return requiredPermission; }
}
