package com.opensocket.aievent.core.security;

import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.util.Map;
import java.util.Optional;

/**
 * Projects one Canonical Root INSTANCE Session into an explicitly addressed Tenant for one request only.
 * No Session, Subject, Principal, Role, Permission or Tenant security epoch is created by this
 * projection. The Root Session keeps its INSTANCE epoch; AuthorizationService validates global and
 * Root-principal freshness while deliberately ignoring Tenant Role-Binding epochs for INSTANCE_ROOT.
 */
public final class R4RouteScopedAuthenticationContext {
    private R4RouteScopedAuthenticationContext() {}

    public static Optional<AuthenticationContext> project(
            AuthenticationContext context,
            ScopeType scopeType,
            Map<String, String> pathVariables) {
        return project(context, scopeType, pathVariables, "");
    }

    /**
     * Projects Root to a request-selected Tenant when a Tenant-scoped route has no tenantId path
     * variable. The hint must come from the server-resolved request context (which already enforces
     * Human workspace/header mismatch rules), never from request JSON.
     */
    public static Optional<AuthenticationContext> project(
            AuthenticationContext context,
            ScopeType scopeType,
            Map<String, String> pathVariables,
            String requestTenantHint) {
        if (scopeType == ScopeType.INSTANCE) {
            if (context.principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT
                    && context.activeTenant().scope() == TenantRef.Scope.TENANT) {
                return Optional.of(new AuthenticationContext(
                        context.subject(), context.principal(), TenantRef.instance(), context.session(),
                        context.assurance(), context.securityEpoch(), context.externalIssuer(),
                        context.issuedAt(), context.expiresAt()));
            }
            return Optional.of(context);
        }
        String routeTenant = pathVariables == null ? "" : pathVariables.getOrDefault("tenantId", "").trim();
        String tenantHint = requestTenantHint == null ? "" : requestTenantHint.trim();
        if (routeTenant.isBlank()) {
            if (context.activeTenant().scope() == TenantRef.Scope.TENANT) return Optional.of(context);
            if (context.principal().principalType() != PrincipalRef.PrincipalType.INSTANCE_ROOT) return Optional.empty();
            if (tenantHint.isBlank()) return Optional.empty();
            routeTenant = tenantHint;
        }
        if (context.activeTenant().scope() == TenantRef.Scope.TENANT) {
            return routeTenant.equals(context.activeTenant().tenantId()) ? Optional.of(context) : Optional.empty();
        }
        if (context.principal().principalType() != PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticationContext(
                context.subject(), context.principal(), TenantRef.tenant(routeTenant), context.session(),
                context.assurance(), context.securityEpoch(), context.externalIssuer(),
                context.issuedAt(), context.expiresAt()));
    }
}
