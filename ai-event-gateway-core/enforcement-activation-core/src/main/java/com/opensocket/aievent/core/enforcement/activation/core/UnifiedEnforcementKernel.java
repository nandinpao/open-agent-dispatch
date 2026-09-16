package com.opensocket.aievent.core.enforcement.activation.core;

import java.util.Objects;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouter;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRoutingContext;
import com.opensocket.aievent.core.enforcement.activation.contract.HardGuardDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.HardSecurityGuard;

public final class UnifiedEnforcementKernel {
    private final HardSecurityGuard hardSecurityGuard;
    private final AuthorityRouter authorityRouter;

    public UnifiedEnforcementKernel(HardSecurityGuard hardSecurityGuard, AuthorityRouter authorityRouter) {
        this.hardSecurityGuard = Objects.requireNonNull(hardSecurityGuard, "hardSecurityGuard");
        this.authorityRouter = Objects.requireNonNull(authorityRouter, "authorityRouter");
    }

    public AuthorityDecision decide(AuthorityRoutingContext context) {
        Objects.requireNonNull(context, "context");
        HardGuardDecision guard = Objects.requireNonNull(hardSecurityGuard.evaluate(context), "hard guard decision");
        if (!guard.allowed()) return AuthorityDecision.hardGuardDenied(context, authorityRouter.currentRevision(), guard.reasonCode());
        return authorityRouter.route(context);
    }
}
