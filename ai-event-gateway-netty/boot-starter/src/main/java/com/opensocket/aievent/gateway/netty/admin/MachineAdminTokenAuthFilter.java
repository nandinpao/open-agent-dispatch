package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Machine-to-machine token guard for Netty Admin, cluster, and internal HTTP endpoints. */
@Component
@Order(10)
public class MachineAdminTokenAuthFilter implements Filter {
    public static final String MACHINE_AUTHENTICATED_ATTRIBUTE = "aiEventGateway.machineAuthenticated";
    private final AdminProperties properties;

    public MachineAdminTokenAuthFilter(AdminProperties properties) { this.properties = properties; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest http) || !(response instanceof HttpServletResponse result)) {
            chain.doFilter(request, response); return;
        }
        if (!properties.machineAuthEnabled() || "OPTIONS".equalsIgnoreCase(http.getMethod())) {
            http.setAttribute(MACHINE_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
            chain.doFilter(request, response); return;
        }
        String path = http.getRequestURI();
        boolean internal = path.startsWith("/internal/");
        boolean protectedPath = path.startsWith("/api/admin/") || path.startsWith("/api/cluster/") || internal;
        if (!protectedPath || "/api/admin/health".equals(path)) { chain.doFilter(request, response); return; }

        String expected = internal ? properties.internalToken() : properties.machineToken();
        if (expected == null || expected.isBlank()) {
            reject(result, 503, "MACHINE_CREDENTIAL_UNAVAILABLE", "Required Netty machine credential is not configured."); return;
        }
        if (!constantTimeEquals(expected, resolveToken(http, internal))) {
            reject(result, 401, "MACHINE_UNAUTHORIZED", "Missing or invalid Netty machine credential."); return;
        }
        http.setAttribute(MACHINE_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(request, response);
    }

    public boolean isAuthorizedMachineToken(String token) {
        return properties.machineAuthEnabled() && constantTimeEquals(properties.machineToken(), token);
    }

    private String resolveToken(HttpServletRequest request, boolean internal) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return authorization.substring(7).trim();
        String machine = request.getHeader("X-Machine-Token");
        if (machine != null && !machine.isBlank()) return machine.trim();
        if (internal) {
            String cluster = request.getHeader("X-Cluster-Token");
            if (cluster != null && !cluster.isBlank()) return cluster.trim();
            String alias = request.getHeader("X-Internal-Token");
            if (alias != null && !alias.isBlank()) return alias.trim();
        }
        return "";
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual((expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8),
                (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
