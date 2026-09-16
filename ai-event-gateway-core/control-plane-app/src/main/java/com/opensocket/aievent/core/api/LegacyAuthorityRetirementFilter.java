package com.opensocket.aievent.core.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fail-closed tombstone for legacy mutation surfaces.
 *
 * <p>Historical GET/read paths remain available for audit, reconciliation and migration evidence,
 * while legacy writes can no longer restore Skill-era routing, directional A2A execution or
 * IntegrationPrincipalScope as a production authority.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public final class LegacyAuthorityRetirementFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();

        Retirement retirement = retirement(method, path);
        if (retirement == null) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.GONE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        String body = "{\"code\":\"" + retirement.code + "\",\"error_code\":\"" + retirement.code
                + "\",\"message\":\"" + escape(retirement.message) + "\"}";
        response.getWriter().write(body);
    }

    private static Retirement retirement(String method, String path) {
        if (!"GET".equals(method) && path.startsWith("/admin/agent-skills")) {
            return new Retirement("LEGACY_SKILL_REGISTRY_RETIRED",
                    "Skill Registry mutation is retired. Use canonical Capability definitions and Agent Capability assignment.");
        }
        if (!"GET".equals(method) && path.matches("/admin/agents/[^/]+/skills(?:/.*)?")) {
            return new Retirement("LEGACY_AGENT_SKILL_AUTHORITY_RETIRED",
                    "Agent Skill mutation/evaluation is retired as a production authority. Use Agent Capability Assignment; historical Skill reads remain audit-only.");
        }
        if ("PUT".equals(method) && path.startsWith("/api/a2a-policies/")) {
            return new Retirement("A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED",
                    "Directional A2A policy mutation is retired. Use capability-first delegation.");
        }
        if ("POST".equals(method) && path.matches("/api/tasks/[^/]+/a2a-requests/?")) {
            return new Retirement("A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED",
                    "Legacy directional A2A request creation is retired. Use /capability-delegations.");
        }
        if ("POST".equals(method) && path.matches("/api/a2a-requests/[^/]+/(approve|reject)/?")) {
            return new Retirement("A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED",
                    "Legacy directional A2A approval/rejection is retired. Historical read and cancellation remain available.");
        }
        if ("PUT".equals(method) && path.matches("/api/integrations/principals/[^/]+/scope/?")) {
            return new Retirement("INTEGRATION_PRINCIPAL_SCOPE_RETIRED",
                    "IntegrationPrincipalScope mutation is retired. Use Project Mapping + one Technical Service Account + Credential; the provider is the final Issue permission authority.");
        }
        return null;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Retirement(String code, String message) { }
}
