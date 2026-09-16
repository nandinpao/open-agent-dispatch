package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementContextPort;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Verified runtime identity for P4RA-E business enforcement. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class SpringSecurityResourceEnforcementContextAdapter implements ResourceEnforcementContextPort {
    private final Clock clock;
    public SpringSecurityResourceEnforcementContextAdapter(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    @Override
    public ResourceEnforcementContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("AUTHENTICATION_REQUIRED");
        }
        AuthenticationContext context = authenticationContext(authentication);
        String correlationId = OpenDispatchRequestContextHolder.current()
                .map(request -> request.correlationId())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("CORRELATION_CONTEXT_REQUIRED"));
        return new ResourceEnforcementContext(context, correlationId, clock.instant());
    }

    private static AuthenticationContext authenticationContext(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticationContext context) return context;
        if (authentication.getPrincipal() instanceof MachineAuthenticationContext machine) return machine.toAuthenticationContext();
        if (authentication.getPrincipal() instanceof MachineExecutionContext execution) return execution.authentication().toAuthenticationContext();
        if (authentication.getDetails() instanceof AuthenticationContext context) return context;
        if (authentication.getDetails() instanceof MachineAuthenticationContext machine) return machine.toAuthenticationContext();
        if (authentication.getDetails() instanceof MachineExecutionContext execution) return execution.authentication().toAuthenticationContext();
        throw new IllegalStateException("VERIFIED_IAM_AUTHENTICATION_CONTEXT_REQUIRED");
    }
}
