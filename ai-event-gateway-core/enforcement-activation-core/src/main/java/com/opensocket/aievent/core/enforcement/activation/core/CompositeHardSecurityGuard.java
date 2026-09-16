package com.opensocket.aievent.core.enforcement.activation.core;

import java.util.List;
import java.util.Objects;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRoutingContext;
import com.opensocket.aievent.core.enforcement.activation.contract.HardGuardDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.HardSecurityGuard;

public final class CompositeHardSecurityGuard implements HardSecurityGuard {
    private final List<HardSecurityGuard> guards;

    public CompositeHardSecurityGuard(List<HardSecurityGuard> guards) {
        if (guards == null || guards.isEmpty()) throw new IllegalArgumentException("at least one hard guard is required");
        this.guards = guards.stream().map(value -> Objects.requireNonNull(value, "hard guard")).toList();
    }

    @Override
    public HardGuardDecision evaluate(AuthorityRoutingContext context) {
        for (HardSecurityGuard guard : guards) {
            HardGuardDecision decision = Objects.requireNonNull(guard.evaluate(context), "hard guard decision");
            if (!decision.allowed()) return decision;
        }
        return HardGuardDecision.allow("ALL_HARD_GUARDS_PASSED");
    }
}
