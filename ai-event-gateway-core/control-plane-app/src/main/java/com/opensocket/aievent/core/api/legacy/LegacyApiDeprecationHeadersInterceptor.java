package com.opensocket.aievent.core.api.legacy;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Adds explicit deprecation metadata to legacy/diagnostic routing APIs without
 * changing their response body or controller behavior.
 *
 * <p>Phase 4-3 keeps these endpoints callable for backward-compatible debug and
 * historical verification, but makes their successor path visible to API
 * clients and Admin UI callers.</p>
 */
public final class LegacyApiDeprecationHeadersInterceptor implements HandlerInterceptor {
    public static final String CURRENT_MODEL = "SOURCE_FLOW_AGENT_POOL";
    public static final String DEPRECATION_STATUS = "DEPRECATED_LEGACY_ROUTING_API";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<LegacyRouteDeprecation> ROUTES = List.of(
            new LegacyRouteDeprecation(
                    "/admin/dispatch-contracts/**",
                    "dispatch-contract",
                    "/admin/dispatch-flows",
                    "Source Flow / Agent Pool setup"),
            new LegacyRouteDeprecation(
                    "/admin/dispatch-contract/**",
                    "dispatch-contract",
                    "/admin/dispatch-flows",
                    "Source Flow / Agent Pool setup"),
            new LegacyRouteDeprecation(
                    "/admin/dispatch-policies/**",
                    "assignment-profile",
                    "/admin/dispatch-flows",
                    "Source Flow rules plus Agent Pool target"),
            new LegacyRouteDeprecation(
                    "/admin/dispatch-governance/cutover/**",
                    "cutover",
                    "/admin/dispatch-flows",
                    "Current Source Flow / Agent Pool release gate"),
            new LegacyRouteDeprecation(
                    "/admin/agents/*/dispatch-eligibility",
                    "governance-eligibility-diagnostic",
                    "/admin/tasks/{taskId}/dispatch-evidence",
                    "Task dispatch evidence and Source Flow simulation"),
            new LegacyRouteDeprecation(
                    "/admin/tasks/*/dispatch-requirements",
                    "governance-eligibility-diagnostic",
                    "/admin/tasks/{taskId}/dispatch-evidence",
                    "Task dispatch evidence and Source Flow simulation"),
            new LegacyRouteDeprecation(
                    "/admin/tasks/*/eligible-agents",
                    "governance-eligibility-diagnostic",
                    "/admin/tasks/{taskId}/dispatch-evidence",
                    "Task dispatch evidence and Source Flow simulation"),
            new LegacyRouteDeprecation(
                    "/admin/tasks/*/eligible-agents-v2",
                    "governance-eligibility-diagnostic",
                    "/admin/tasks/{taskId}/dispatch-evidence",
                    "Task dispatch evidence and Source Flow simulation"),
            new LegacyRouteDeprecation(
                    "/admin/enforce/legacy-final-report",
                    "legacy-final-report",
                    "/admin/dispatch-flows",
                    "Current release gate and dispatch evidence reports")
    );

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        for (LegacyRouteDeprecation route : ROUTES) {
            if (PATH_MATCHER.match(route.pattern(), requestPath)) {
                applyHeaders(response, route);
                break;
            }
        }
        return true;
    }

    private void applyHeaders(HttpServletResponse response, LegacyRouteDeprecation route) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "<" + route.replacementPath() + ">; rel=\"successor-version\"");
        response.setHeader("X-OpenDispatch-Legacy-Category", route.legacyCategory());
        response.setHeader("X-OpenDispatch-Legacy-Status", DEPRECATION_STATUS);
        response.setHeader("X-OpenDispatch-Replacement", route.replacementDescription());
        response.setHeader("X-OpenDispatch-Current-Model", CURRENT_MODEL);
    }

    private record LegacyRouteDeprecation(String pattern,
                                          String legacyCategory,
                                          String replacementPath,
                                          String replacementDescription) {
    }
}
