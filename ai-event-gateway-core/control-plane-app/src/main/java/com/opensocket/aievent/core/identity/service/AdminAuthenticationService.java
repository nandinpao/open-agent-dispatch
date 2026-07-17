package com.opensocket.aievent.core.identity.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.identity.AdminIdentityProperties;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.dto.AdminLoginRequest;
import com.opensocket.aievent.core.identity.dto.AdminPermissionsResponse;
import com.opensocket.aievent.core.identity.dto.AdminSessionResponse;
import com.opensocket.aievent.core.identity.dto.AdminTenantOption;
import com.opensocket.aievent.core.identity.dto.AdminTenantsResponse;

@Service
public class AdminAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationService.class);
    private static final String AUTHENTICATED_AT = AdminAuthenticationService.class.getName() + ".AUTHENTICATED_AT";

    private final AdminIdentityProperties properties;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final AdminSecurityAuditService audit;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AdminAuthenticationService(AdminIdentityProperties properties,
                                      AuthenticationManager authenticationManager,
                                      SecurityContextRepository securityContextRepository,
                                      SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                      AdminSecurityAuditService audit) {
        this.properties = properties;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.audit = audit;
    }

    public AdminSessionResponse login(AdminLoginRequest loginRequest,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        requireEnabled();
        Authentication candidate = UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.username().trim(), loginRequest.password());
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(candidate);
        } catch (AuthenticationException ex) {
            audit.record("LOGIN", "FAILED", loginRequest.username(), null, request, ex.getClass().getSimpleName());
            throw ex;
        }
        sessionAuthenticationStrategy.onAuthentication(authenticated, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authenticated);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(false);
        OffsetDateTime authenticatedAt = OffsetDateTime.now();
        if (session != null) {
            session.setAttribute(AUTHENTICATED_AT, authenticatedAt);
            session.setMaxInactiveInterval(Math.toIntExact(properties.getSessionTimeout().toSeconds()));
        }
        AdminPrincipal principal = requirePrincipal(authenticated);
        log.info("core admin login accepted userId={} username={} selectedTenant={}",
                principal.userId(), principal.getUsername(), principal.selectedTenantId());
        audit.record("LOGIN", "SUCCESS", principal.getUsername(), principal, request, "");
        return sessionResponse(principal, authenticatedAt, request);
    }

    public AdminSessionResponse current(Authentication authentication, HttpServletRequest request) {
        AdminPrincipal principal = requirePrincipal(authentication);
        return sessionResponse(principal, authenticatedAt(request), request);
    }

    public AdminPermissionsResponse permissions(Authentication authentication) {
        AdminPrincipal principal = requirePrincipal(authentication);
        return new AdminPermissionsResponse(roleNames(principal), principal.permissions());
    }

    public AdminTenantsResponse tenants(Authentication authentication) {
        AdminPrincipal principal = requirePrincipal(authentication);
        List<AdminTenantOption> tenants = principal.allowedTenantIds().stream()
                .sorted()
                .map(tenantId -> new AdminTenantOption(tenantId, tenantId.equals(principal.selectedTenantId())))
                .toList();
        return new AdminTenantsResponse(principal.selectedTenantId(), tenants);
    }

    public AdminSessionResponse selectTenant(String tenantId,
                                             Authentication authentication,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        AdminPrincipal current = requirePrincipal(authentication);
        AdminPrincipal updated;
        try {
            updated = current.withSelectedTenant(tenantId);
        } catch (IllegalArgumentException ex) {
            audit.record("TENANT_SELECT", "DENIED", current.getUsername(), current, request, "tenant=" + tenantId);
            throw ex;
        }
        UsernamePasswordAuthenticationToken updatedAuthentication = new UsernamePasswordAuthenticationToken(
                updated, null, updated.getAuthorities());
        updatedAuthentication.setDetails(authentication.getDetails());

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(updatedAuthentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        log.info("core admin tenant selected userId={} tenantId={}", updated.userId(), updated.selectedTenantId());
        audit.record("TENANT_SELECT", "SUCCESS", updated.getUsername(), updated, request, "tenant=" + updated.selectedTenantId());
        return sessionResponse(updated, authenticatedAt(request), request);
    }

    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        String username = authentication == null ? "anonymous" : authentication.getName();
        AdminPrincipal principal = authentication != null && authentication.getPrincipal() instanceof AdminPrincipal value ? value : null;
        audit.record("LOGOUT", "SUCCESS", username, principal, request, "");
        logoutHandler.logout(request, response, authentication);
        log.info("core admin logout completed username={}", username);
    }

    private AdminSessionResponse sessionResponse(AdminPrincipal principal,
                                                 OffsetDateTime authenticatedAt,
                                                 HttpServletRequest request) {
        return new AdminSessionResponse(
                "SESSION",
                principal.userId(),
                principal.getUsername(),
                principal.displayName(),
                roleNames(principal),
                principal.permissions(),
                principal.allowedTenantIds(),
                principal.selectedTenantId(),
                authenticatedAt,
                expiresAt(request));
    }

    private OffsetDateTime expiresAt(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return OffsetDateTime.now().plus(properties.getSessionTimeout());
        }
        long expiresAtMillis = session.getLastAccessedTime() + (session.getMaxInactiveInterval() * 1000L);
        return Instant.ofEpochMilli(expiresAtMillis).atOffset(ZoneOffset.UTC);
    }

    private Set<String> roleNames(AdminPrincipal principal) {
        return principal.roles().stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toUnmodifiableSet());
    }

    private OffsetDateTime authenticatedAt(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AUTHENTICATED_AT) instanceof OffsetDateTime value) {
            return value;
        }
        return OffsetDateTime.now();
    }

    private AdminPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new IllegalStateException("Authenticated Core Admin principal is required.");
        }
        return principal;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new AdminAuthenticationDisabledException();
        }
    }
}
