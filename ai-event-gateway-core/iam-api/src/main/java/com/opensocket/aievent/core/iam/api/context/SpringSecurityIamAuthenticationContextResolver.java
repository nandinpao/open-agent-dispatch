package com.opensocket.aievent.core.iam.api.context;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves only the stable IAM AuthenticationContext; it never reconstructs authority from request headers. */
public final class SpringSecurityIamAuthenticationContextResolver implements IamAuthenticationContextResolver {
    @Override public Optional<AuthenticationContext> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        if (authentication.getPrincipal() instanceof AuthenticationContext context) return Optional.of(context);
        if (authentication.getDetails() instanceof AuthenticationContext context) return Optional.of(context);
        return Optional.empty();
    }
}
