package com.opensocket.aievent.core.uicapability.runtime;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContext;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContextPort;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "projection-api-enabled"}, havingValue = "true")
public final class SpringSecurityUiCapabilityApiContextAdapter implements UiCapabilityApiContextPort {
    private final Clock clock;
    public SpringSecurityUiCapabilityApiContextAdapter(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }
    @Override public UiCapabilityApiContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new IllegalStateException("AUTHENTICATION_REQUIRED");
        AuthenticationContext context = authentication.getPrincipal() instanceof AuthenticationContext value ? value
                : authentication.getDetails() instanceof AuthenticationContext value ? value
                : null;
        if (context == null) throw new IllegalStateException("VERIFIED_IAM_AUTHENTICATION_CONTEXT_REQUIRED");
        String correlationId = OpenDispatchRequestContextHolder.current().map(request -> request.correlationId())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("CORRELATION_CONTEXT_REQUIRED"));
        return new UiCapabilityApiContext(context, correlationId, clock.instant());
    }
}
