package com.opensocket.aievent.core.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CoreInternalTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CoreInternalTokenAuthenticationFilter.class);

    private final CoreInternalSecurityProperties properties;
    private final CoreInternalTokenVerifier verifier;

    public CoreInternalTokenAuthenticationFilter(CoreInternalSecurityProperties properties,
                                                 CoreInternalTokenVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated() && !(existing instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        CoreInternalTokenVerifier.Verification verification = verifier.verify(request);
        if (!verification.required()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!verification.accepted()) {
            audit(request, verification.role(), false, verification.reason());
            unauthorized(response, "Invalid internal token");
            return;
        }

        CoreInternalSecurityRole role = verification.role();
        var authentication = new UsernamePasswordAuthenticationToken(
                "core-internal-" + role.name().toLowerCase(),
                "N/A",
                authoritiesFor(role));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        audit(request, role, true, "accepted");
        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> authoritiesFor(CoreInternalSecurityRole role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(CoreInternalSecurityProperties.authority(role)));
        if (role == CoreInternalSecurityRole.RECOVERY_OPERATOR || role == CoreInternalSecurityRole.RECOVERY_ADMIN || role == CoreInternalSecurityRole.RECOVERY_APPROVER) {
            authorities.add(new SimpleGrantedAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR)));
        }
        if (role == CoreInternalSecurityRole.RECOVERY_ADMIN) {
            authorities.add(new SimpleGrantedAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.RECOVERY_OPERATOR)));
        }
        return authorities;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private void audit(HttpServletRequest request, CoreInternalSecurityRole role, boolean accepted, String reason) {
        if (!properties.isAuditLogEnabled()) {
            return;
        }
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (accepted) {
            log.info("core internal security accepted role={} method={} path={} remote={}", role, request.getMethod(), path, remote);
        } else {
            log.warn("core internal security rejected role={} reason={} method={} path={} remote={}", role, reason, request.getMethod(), path, remote);
        }
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
