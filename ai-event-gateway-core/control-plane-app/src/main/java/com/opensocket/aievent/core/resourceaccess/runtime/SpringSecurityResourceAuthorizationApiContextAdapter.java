package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.resourceaccess.api.ResourceAuthorizationApiContext;
import com.opensocket.aievent.core.resourceaccess.api.ResourceAuthorizationApiContextPort;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Supplies the decision API with verified Spring Security identity and server-generated correlation evidence.
 * Request headers and request bodies are never used to reconstruct a principal or active Tenant.
 */
@Component
@ConditionalOnProperty(
        prefix = "resource-access",
        name = {"enabled", "decision-api-enabled"},
        havingValue = "true")
public final class SpringSecurityResourceAuthorizationApiContextAdapter
        implements ResourceAuthorizationApiContextPort {
    private final Clock clock;

    public SpringSecurityResourceAuthorizationApiContextAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ResourceAuthorizationApiContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("AUTHENTICATION_REQUIRED");
        }
        AuthenticationContext context = authenticationContext(authentication);
        String correlationId = OpenDispatchRequestContextHolder.current()
                .map(request -> request.correlationId())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("CORRELATION_CONTEXT_REQUIRED"));
        return new ResourceAuthorizationApiContext(context, correlationId, clock.instant());
    }

    private static AuthenticationContext authenticationContext(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticationContext context) return context;
        if (authentication.getDetails() instanceof AuthenticationContext context) return context;
        throw new IllegalStateException("VERIFIED_IAM_AUTHENTICATION_CONTEXT_REQUIRED");
    }
}
