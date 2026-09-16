package com.opensocket.aievent.core.iam.api.context;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/** Builds the canonical request context and honors a route-scoped Root Tenant projection from the R3/R4 filter. */
public final class IamApiRequestContextFactory {
    public static final String AUTHENTICATION_CONTEXT_ATTRIBUTE =
            "com.opensocket.aievent.iam.authorizationAuthenticationContext";
    public static final String CREDENTIAL_PERMISSION_BOUNDARY_ATTRIBUTE =
            "com.opensocket.aievent.iam.credentialPermissionBoundary";

    private final IamAuthenticationContextResolver resolver;
    private final Clock clock;

    public IamApiRequestContextFactory(IamAuthenticationContextResolver resolver, Clock clock) {
        this.resolver = resolver;
        this.clock = clock;
    }

    public IamApiRequestContext from(HttpServletRequest request) {
        String correlation = first(request.getHeader("X-Correlation-Id"), request.getHeader("X-Request-Id"));
        if (correlation == null || correlation.isBlank()) correlation = UUID.randomUUID().toString();
        Object projected = request.getAttribute(AUTHENTICATION_CONTEXT_ATTRIBUTE);
        Optional<AuthenticationContext> authentication = projected instanceof AuthenticationContext context
                ? Optional.of(context)
                : resolver.resolve();
        Object boundaryAttribute = request.getAttribute(CREDENTIAL_PERMISSION_BOUNDARY_ATTRIBUTE);
        Set<String> boundary = boundaryAttribute instanceof Set<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : Set.of();
        return new IamApiRequestContext(authentication, correlation,
                request.getHeader("Idempotency-Key"), request.getHeader("X-Audit-Reason"),
                clientAddress(request), request.getHeader("User-Agent"), clock.instant(), boundary);
    }

    private String clientAddress(HttpServletRequest request) {
        return Optional.ofNullable(request.getRemoteAddr()).orElse("");
    }

    private String first(String primary, String secondary) {
        return primary == null || primary.isBlank() ? secondary : primary;
    }
}
