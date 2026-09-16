package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/** Canonical runtime machine identity plus immutable delegation evidence. */
public record MachineExecutionContext(
        MachineAuthenticationContext authentication,
        MachineDelegationContext delegation
) {
    public MachineExecutionContext {
        Objects.requireNonNull(authentication, "authentication");
        delegation = delegation == null ? MachineDelegationContext.origin(authentication.principal()) : delegation;
        if (!delegation.executingPrincipal().equals(authentication.principal())) {
            throw new IllegalArgumentException("delegation executing principal must match authenticated machine principal");
        }
    }

    public static MachineExecutionContext direct(MachineAuthenticationContext authentication) {
        return new MachineExecutionContext(authentication, MachineDelegationContext.origin(authentication.principal()));
    }
}
