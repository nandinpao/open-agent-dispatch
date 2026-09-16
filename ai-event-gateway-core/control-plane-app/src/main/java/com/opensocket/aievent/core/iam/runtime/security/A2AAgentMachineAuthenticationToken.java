package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Spring Security carrier for a Core-governed Agent credential on the canonical A2A request route. */
public final class A2AAgentMachineAuthenticationToken extends AbstractAuthenticationToken {
    private final MachineAuthenticationContext machine;

    public A2AAgentMachineAuthenticationToken(MachineAuthenticationContext machine) {
        super(List.of(new SimpleGrantedAuthority("ROLE_AGENT_MACHINE")));
        this.machine = java.util.Objects.requireNonNull(machine, "machine");
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return "[PROTECTED]"; }
    @Override public MachineAuthenticationContext getPrincipal() { return machine; }
}
