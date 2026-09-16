package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Spring Security projection of the canonical framework-free MachineAuthenticationContext. */
public final class EventIntakeMachineAuthenticationToken extends AbstractAuthenticationToken {
    private final MachineAuthenticationContext principal;

    public EventIntakeMachineAuthenticationToken(MachineAuthenticationContext principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return "N/A"; }
    @Override public MachineAuthenticationContext getPrincipal() { return principal; }
    @Override public String getName() { return principal.principal().principalId(); }
}
