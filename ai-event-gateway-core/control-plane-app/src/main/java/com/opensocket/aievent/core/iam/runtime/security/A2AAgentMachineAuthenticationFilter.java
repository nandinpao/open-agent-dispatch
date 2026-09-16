package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.agent.governance.AgentAuthorizationDecision;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationDenyReason;
import com.opensocket.aievent.core.agent.governance.AgentConnectionAuthorizationRequest;
import com.opensocket.aievent.core.agent.governance.AgentConnectionAuthorizationResult;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Compatibility guard for the retired Agent-over-HTTP A2A bearer flow.
 * Agent runtime credentials must never be exported to external clients; Agent-originated A2A now
 * enters through the authenticated Gateway runtime session and Core internal relay.
 */
public final class A2AAgentMachineAuthenticationFilter extends OncePerRequestFilter {
    public static final String AGENT_ID_HEADER = "X-Agent-Id";
    private static final Pattern A2A_REQUEST_PATH = Pattern.compile("^/api/tasks/[^/]+/a2a-requests/?$");
    private final AgentGovernanceService governance;
    private final ServletIamSessionCookieAdapter sessionCookies;

    public A2AAgentMachineAuthenticationFilter(
            AgentGovernanceService governance,
            ServletIamSessionCookieAdapter sessionCookies) {
        this.governance = java.util.Objects.requireNonNull(governance, "governance");
        this.sessionCookies = java.util.Objects.requireNonNull(sessionCookies, "sessionCookies");
    }

    public static boolean hasAgentA2ABearer(HttpServletRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI();
        String token = bearer(request);
        return path != null && A2A_REQUEST_PATH.matcher(path).matches()
                && !token.isBlank() && !token.startsWith("odp_pat_");
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !hasAgentA2ABearer(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String correlationId = correlation(request);
        request.setAttribute("opendispatch.correlationId", correlationId);
        reject(response, correlationId, 403, "AGENT_A2A_HTTP_CREDENTIAL_FORBIDDEN",
                "Agent runtime credentials are not accepted on the public A2A API. "
                + "Agent-originated A2A requests must use the authenticated Gateway runtime channel.");
    }

    private static boolean credentialFailure(AgentAuthorizationDenyReason reason) {
        return reason == AgentAuthorizationDenyReason.AGENT_ID_REQUIRED
                || reason == AgentAuthorizationDenyReason.CREDENTIAL_REQUIRED
                || reason == AgentAuthorizationDenyReason.CREDENTIAL_INVALID
                || reason == AgentAuthorizationDenyReason.CREDENTIAL_REVOKED
                || reason == AgentAuthorizationDenyReason.FINGERPRINT_MISMATCH;
    }
    private static String bearer(HttpServletRequest request) {
        String value = request.getHeader("Authorization");
        return value != null && value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : "";
    }
    private static String correlation(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        if (value == null || value.isBlank()) value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static void reject(HttpServletResponse response, String correlationId, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer realm=\"OpenDispatch Agent A2A\", error=\"invalid_token\"");
        response.getWriter().write("{\"code\":\"" + escape(code) + "\",\"error_code\":\"" + escape(code)
                + "\",\"message\":\"" + escape(message) + "\",\"correlationId\":\"" + escape(correlationId) + "\"}");
    }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
