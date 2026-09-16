package com.opensocket.aievent.core.enforcement.activation.contract;

@FunctionalInterface
public interface HardSecurityGuard {
    HardGuardDecision evaluate(AuthorityRoutingContext context);
}
