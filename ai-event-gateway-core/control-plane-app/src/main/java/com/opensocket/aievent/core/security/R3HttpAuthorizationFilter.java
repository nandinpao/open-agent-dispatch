package com.opensocket.aievent.core.security;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.domain.LegacyDecision;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.runtime.security.IamRuntimeAuthenticationToken;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the single R3 Atomic Permission authority for Human Admin HTTP APIs. */
public final class R3HttpAuthorizationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(R3HttpAuthorizationFilter.class);
    private static final Set<String> PUBLIC_GET = Set.of(
            "/api/bootstrap/status", "/api/session/csrf",
            "/api/session/federation/providers", "/api/session/federation/oidc/callback",
            "/api/platform/runtime-capabilities");
    private static final Set<String> PUBLIC_POST = Set.of(
            "/api/session/login", "/api/session/federation/oidc/start", "/api/session/mfa/verify",
            "/api/session/forgot-password", "/api/session/reset-password",
            "/api/session/activate-invitation");
    private static final Set<String> NON_HUMAN_ROUTE_PREFIXES = Set.of(
            "/api/events/",
            "/api/external/provider-webhooks/",
            "/oauth/",
            "/oauth2/",
            "/.well-known/",
            "/internal/",
            "/actuator/");
    private static final Set<String> CANONICAL_SELF_SERVICE = Set.of(
            "GET /api/session",
            "GET /api/session/entitlements",
            "GET /api/session/preflight",
            "POST /api/session/change-password",
            "POST /api/session/mfa/enrollment/start",
            "POST /api/session/mfa/enrollment/confirm",
            "POST /api/session/logout",
            "POST /api/session/logout-all",
            "POST /api/session/switch-tenant",
            "POST /api/bootstrap/complete",
            "POST /api/bootstrap/root/mfa",
            "POST /api/bootstrap/root/mfa/confirm",
            "POST /api/bootstrap/tenant",
            "POST /api/bootstrap/tenant-admin");

    private final R3HumanApiPermissionRegistry registry;
    private final IamSecurityAdapter authorization;
    private final R3CompatibilityDecisionEvaluator compatibility;

    public R3HttpAuthorizationFilter(
            R3HumanApiPermissionRegistry registry,
            IamSecurityAdapter authorization,
            R3CompatibilityDecisionEvaluator compatibility) {
        this.registry = registry;
        this.authorization = authorization;
        this.compatibility = compatibility;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        String path = requestPath(request);
        if (NON_HUMAN_ROUTE_PREFIXES.stream().anyMatch(path::startsWith)) return true;
        if (isAuthenticatedAgentA2ARequest(request, path)) return true;
        if (CANONICAL_SELF_SERVICE.contains(request.getMethod().toUpperCase() + " " + path)) return true;
        if (HttpMethod.GET.matches(request.getMethod()) && PUBLIC_GET.contains(path)) return true;
        return HttpMethod.POST.matches(request.getMethod()) && PUBLIC_POST.contains(path);
    }


    private static boolean isAuthenticatedAgentA2ARequest(HttpServletRequest request, String path) {
        if (!HttpMethod.POST.matches(request.getMethod()) || path == null
                || !path.matches("^/api/tasks/[^/]+/a2a-requests/?$")) return false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof MachineAuthenticationContext machine)) return false;
        MachinePrincipalType type = machine.principal().principalType();
        return type == MachinePrincipalType.AGENT || type == MachinePrincipalType.A2A_AGENT;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = requestPath(request);
        String correlationId = correlationId(request);
        response.setHeader("X-Correlation-Id", correlationId);
        request.setAttribute("opendispatch.correlationId", correlationId);
        R3HttpPermissionRule.Resolved resolved = registry.resolve(request.getMethod(), path).orElse(null);
        if (resolved == null) {
            log.warn("r3_authorization_route_unmapped method={} path={} correlationId={}", request.getMethod(), path, correlationId);
            writeError(response, 403, "IAM_ROUTE_PERMISSION_UNMAPPED",
                    "No canonical Atomic Permission is mapped to this Human Admin API route.", "", "");
            return;
        }

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (isInternalMachine(current)) {
            if (!hasAnyAuthority(current, resolved.rule().internalRoles())) {
                writeError(response, 403, "INTERNAL_ROUTE_ROLE_DENIED",
                        "The internal machine role is not permitted for this route.", resolved.rule().permission(), "");
                return;
            }
            request.setAttribute("r3.authorization.mode", "INTERNAL_MACHINE");
            request.setAttribute("r3.authorization.permission", resolved.rule().permission());
            chain.doFilter(request, response);
            return;
        }

        if (!(current instanceof IamRuntimeAuthenticationToken token)
                || !current.isAuthenticated()) {
            log.warn("r3_authorization_authentication_required method={} path={} permission={} correlationId={}",
                    request.getMethod(), path, resolved.rule().permission(), correlationId);
            writeError(response, 401, "AUTHENTICATION_REQUIRED",
                    "Canonical IAM authentication is required.", resolved.rule().permission(), "");
            return;
        }

        if (!token.permitsCredentialPermission(resolved.rule().permission())) {
            writeError(response, 403, "AUTH_TOKEN_SCOPE_INSUFFICIENT",
                    "The presented credential does not grant this Atomic Permission.", resolved.rule().permission(), "");
            return;
        }

        AuthenticationContext sessionContext = (AuthenticationContext) token.getPrincipal();
        String requestTenantHint = OpenDispatchRequestContextHolder.current()
                .map(value -> value.tenantId() == null ? "" : value.tenantId())
                .orElse("");
        AuthenticationContext context = R4RouteScopedAuthenticationContext.project(
                sessionContext, resolved.rule().scopeType(), resolved.pathVariables(), requestTenantHint).orElse(null);
        if (context == null) {
            log.warn("r3_authorization_tenant_context_required method={} path={} permission={} tenantHint={} correlationId={}",
                    request.getMethod(), path, resolved.rule().permission(), requestTenantHint, correlationId);
            writeError(response, 403, "AUTH_TENANT_CONTEXT_REQUIRED",
                    "A valid Tenant context is required for this Tenant-scoped API route.",
                    resolved.rule().permission(), "");
            return;
        }
        request.setAttribute(IamApiRequestContextFactory.AUTHENTICATION_CONTEXT_ATTRIBUTE, context);
        Map<String, String> requestContext = requestContext(request, resolved);
        if (resolved.rule().controllerScoped()) {
            // Collection/body/resolved-membership routes cannot express the final Department/Group
            // scope from the URL alone. The policy explicitly marks these routes for a second-stage,
            // resource-aware IamPermissionGuard decision inside the controller. Authentication and
            // Tenant route projection are still fail-closed here; no Role or Permission is synthesized.
            request.setAttribute("r3.authorization.mode", "TARGET_CONTROLLER_SCOPED");
            request.setAttribute("r3.authorization.permission", resolved.rule().permission());
            request.setAttribute("r3.authorization.deferredScope", "CONTROLLER");
            chain.doFilter(request, response);
            return;
        }
        AuthorizationDecision decision;
        try {
            decision = inTenant(context, () -> authorization.authorize(
                    context,
                    resolved.rule().permission(),
                    resolved.rule().resourceType(),
                    resolved.resourceId(),
                    resolved.rule().scopeType(),
                    effectiveScopeId(context, resolved),
                    requestContext));
        } catch (RuntimeException failure) {
            String diagnostic = authorizationInfrastructureDiagnostic(failure);
            log.error("R3 authorization infrastructure failed method={} path={} permission={} correlationId={} diagnostic={}",
                    request.getMethod(), path, resolved.rule().permission(), correlationId, diagnostic, failure);
            response.setHeader("X-Authorization-Diagnostic", diagnostic);
            writeError(response, 503, "AUTHORIZATION_SERVICE_UNAVAILABLE",
                    "Access management is temporarily unavailable because the authorization service could not complete the request.",
                    resolved.rule().permission(), "");
            return;
        }

        recordShadowEvidence(context, resolved, decision, requestContext, path);
        request.setAttribute("r3.authorization.mode", "TARGET_ONLY");
        request.setAttribute("r3.authorization.permission", resolved.rule().permission());
        request.setAttribute("r3.authorization.decisionId", decision.decisionId());
        log.info("r3_authorization_decision method={} path={} permission={} resourceType={} resourceId={} scopeType={} scopeId={} effect={} reasonCode={} decisionId={} subjectId={} tenantId={} correlationId={}",
                request.getMethod(), path, resolved.rule().permission(), resolved.rule().resourceType(), resolved.resourceId(),
                resolved.rule().scopeType(), effectiveScopeId(context, resolved), decision.effect(), decision.reasonCode(),
                decision.decisionId(), context.subject().subjectId(),
                context.activeTenant().scope() == TenantRef.Scope.TENANT ? context.activeTenant().tenantId() : "INSTANCE",
                correlationId);
        if (decision.effect() != AuthorizationDecision.Effect.ALLOW) {
            writeError(response, decisionHttpStatus(decision.reasonCode()), decision.reasonCode(),
                    decisionMessage(decision.reasonCode()),
                    resolved.rule().permission(), decision.decisionId());
            return;
        }
        chain.doFilter(request, response);
    }

    private void recordShadowEvidence(
            AuthenticationContext context,
            R3HttpPermissionRule.Resolved resolved,
            AuthorizationDecision decision,
            Map<String, String> requestContext,
            String path) {
        try {
            LegacyDecision legacy = compatibility.evaluate(context, resolved.rule().legacyRoles());
            inTenant(context, () -> {
                authorization.recordShadow(
                        context, legacy, decision, resolved.rule().permission(), resolved.rule().resourceType(),
                        resolved.resourceId(), resolved.rule().scopeType(), effectiveScopeId(context, resolved),
                        path, requestContext);
                return null;
            });
        } catch (RuntimeException shadowFailure) {
            log.warn("R3 shadow evidence failed permission={} path={} reason={}",
                    resolved.rule().permission(), path, shadowFailure.getMessage());
        }
    }


    private static String effectiveScopeId(
            AuthenticationContext context,
            R3HttpPermissionRule.Resolved resolved) {
        return switch (resolved.rule().scopeType()) {
            case INSTANCE -> "INSTANCE";
            case TENANT -> context.activeTenant().scope() == TenantRef.Scope.TENANT
                    ? context.activeTenant().tenantId() : "";
            case DEPARTMENT, DEPARTMENT_SUBTREE, GROUP -> resolved.scopeId();
        };
    }

    private static <T> T inTenant(AuthenticationContext context, Supplier<T> work) {
        String persistenceScope = context.activeTenant().scope() == TenantRef.Scope.TENANT
                ? context.activeTenant().tenantId() : "INSTANCE";
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(
                        persistenceScope, "r3-authz:" + context.subject().subjectId()),
                work);
    }

    private static Map<String, String> requestContext(
            HttpServletRequest request,
            R3HttpPermissionRule.Resolved resolved) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("correlationId", correlationId(request));
        context.put("clientAddress", request.getRemoteAddr() == null ? "" : request.getRemoteAddr());
        context.put("method", request.getMethod());
        context.put("routePattern", resolved.rule().routeTemplate());
        context.put("highRisk", Boolean.toString(resolved.rule().highRisk()));
        return Map.copyOf(context);
    }

    private static boolean isInternalMachine(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Object principal = authentication.getPrincipal();
        return principal instanceof String value && value.startsWith("core-internal-");
    }

    private static boolean hasAnyAuthority(Authentication authentication, Set<String> roles) {
        if (roles == null || roles.isEmpty()) return false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String code = authority.getAuthority();
            for (String role : roles) {
                if (("ROLE_" + role).equals(code)) return true;
            }
        }
        return false;
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "/" : request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (uri.isBlank()) return "/";
        return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private static String correlationId(HttpServletRequest request) {
        Object existing = request.getAttribute("opendispatch.correlationId");
        if (existing instanceof String value && !value.isBlank()) return value;
        String value = header(request, "X-Correlation-Id");
        if (value.isBlank()) value = header(request, "X-Request-Id");
        return value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    /** Safe, non-secret classification for qualified-runtime diagnosis. */
    static String authorizationInfrastructureDiagnostic(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            String message = current.getMessage() == null ? "" : current.getMessage();
            String type = current.getClass().getName();
            if (message.contains("TENANT_TRANSACTION_REQUIRED")) return "AUTHZ_TRANSACTION_BOUNDARY";
            if (message.contains("TENANT_CONTEXT_REQUIRED")) return "AUTHZ_TENANT_CONTEXT";
            if (message.contains("RBAC_DECISION_AUDIT_INSERT_FAILED")) return "AUTHZ_AUDIT_WRITE";
            if (message.contains("AUTHORIZATION_DECISION_REQUIRED")) return "AUTHZ_DECISION_MISSING";
            if (type.contains("PSQLException") || type.contains("SQLException")) return "AUTHZ_DATABASE";
            if (type.contains("MyBatis") || type.contains("BindingException") || type.contains("PersistenceException")) {
                return "AUTHZ_PERSISTENCE";
            }
        }
        return "AUTHZ_INTERNAL";
    }

    static int decisionHttpStatus(String reasonCode) {
        return "AUTH_POLICY_VERSION_STALE".equals(reasonCode) ? 401 : 403;
    }

    static String decisionMessage(String reasonCode) {
        return switch (reasonCode == null ? "" : reasonCode) {
            case "AUTH_POLICY_VERSION_STALE" ->
                    "The authenticated session security epoch is stale because authorization policy changed. Sign in again.";
            case "AUTH_TENANT_MISMATCH" ->
                    "The requested Tenant and Scope do not match the authenticated session.";
            case "AUTH_PERMISSION_UNKNOWN" ->
                    "The requested Atomic Permission is not present in the active Permission Catalog.";
            case "AUTH_PERMISSION_DISABLED" ->
                    "The requested Atomic Permission is disabled in the active Permission Catalog.";
            case "AUTH_SCOPE_UNSUPPORTED", "ROLE_BINDING_SCOPE_INVALID" ->
                    "The requested Scope is not supported by this Atomic Permission.";
            default -> "No effective Role Binding grants this Atomic Permission and Scope.";
        };
    }

    private static void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            String permission,
            String decisionId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = response.getHeader("X-Correlation-Id");
        response.getWriter().write(
                "{\"code\":\"" + escape(code) + "\","
                        + "\"error_code\":\"" + escape(code) + "\","
                        + "\"message\":\"" + escape(message) + "\","
                        + "\"permission\":\"" + escape(permission) + "\","
                        + "\"decision_id\":\"" + escape(decisionId) + "\","
                        + "\"correlationId\":\"" + escape(correlationId) + "\"}");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
