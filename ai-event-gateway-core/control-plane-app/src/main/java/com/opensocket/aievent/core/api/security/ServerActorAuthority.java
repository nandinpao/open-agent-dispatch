package com.opensocket.aievent.core.api.security;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Resolves mutation/audit actors from authenticated server state, never request JSON. */
public final class ServerActorAuthority {
    private ServerActorAuthority() {
    }

    public static String requireActorId() {
        String tenantActor = IamTenantContextHolder.current()
                .map(context -> normalized(context.actorId()))
                .orElse(null);
        if (tenantActor != null) {
            return tenantActor;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String principal = normalized(authentication.getName());
            if (principal != null && !"anonymousUser".equalsIgnoreCase(principal)) {
                return principal;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATED_ACTOR_REQUIRED");
    }

    public static void rejectSpoofedActor(String requestActorId, String authoritativeActorId) {
        String supplied = normalized(requestActorId);
        if (supplied != null && !supplied.equals(authoritativeActorId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_ACTOR_NOT_AUTHORITATIVE; actor identity is derived from the authenticated server session");
        }
    }

    private static String normalized(String value) {
        if (value == null) {
            return null;
        }
        String checked = value.trim();
        return checked.isEmpty() ? null : checked;
    }
}
