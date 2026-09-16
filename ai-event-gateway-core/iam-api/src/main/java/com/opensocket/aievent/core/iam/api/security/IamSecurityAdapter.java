package com.opensocket.aievent.core.iam.api.security;

import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.ShadowAuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.domain.LegacyDecision;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.rbac.domain.ShadowDecisionRecord;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.Clock;
import java.util.*;

/** Bridges a verified AuthenticationContext to the framework-free AuthorizationPort. */
public final class IamSecurityAdapter {
    private final AuthorizationPort authorization;
    private final Optional<ShadowAuthorizationPort> shadow;
    private final Clock clock;

    public IamSecurityAdapter(AuthorizationPort authorization, ShadowAuthorizationPort shadow, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.shadow = Optional.ofNullable(shadow);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthorizationDecision authorize(AuthenticationContext authentication, String permission,
                                           String resourceType, String resourceId,
                                           ScopeType scopeType, String scopeId,
                                           Map<String,String> requestContext) {
        validateAuthentication(authentication);
        ScopeType effectiveType = scopeType == null
                ? (authentication.activeTenant().scope() == TenantRef.Scope.INSTANCE ? ScopeType.INSTANCE : ScopeType.TENANT)
                : scopeType;
        String effectiveScopeId = normalizeScopeId(authentication.activeTenant(), effectiveType, scopeId);
        Map<String,String> context = requestContext == null ? Map.of() : Map.copyOf(requestContext);
        AuthorizationRequest request = new AuthorizationRequest(authentication.principal(), authentication.activeTenant(),
                permission, resourceType, resourceId, effectiveType.name(), effectiveScopeId,
                authentication.securityEpoch(), context);
        return authorization.authorize(request);
    }

    public AuthorizationDecision require(AuthenticationContext authentication, String permission,
                                         String resourceType, String resourceId,
                                         ScopeType scopeType, String scopeId,
                                         Map<String,String> requestContext) {
        AuthorizationDecision decision = authorize(authentication, permission, resourceType, resourceId,
                scopeType, scopeId, requestContext);
        if (decision.effect() != AuthorizationDecision.Effect.ALLOW) throw new IamAuthorizationException(decision, permission);
        return decision;
    }

    public Optional<ShadowDecisionRecord> shadow(AuthenticationContext authentication, LegacyDecision legacyDecision,
                                                  String permission, String resourceType, String resourceId,
                                                  ScopeType scopeType, String scopeId, String route,
                                                  Map<String,String> requestContext) {
        if (shadow.isEmpty()) return Optional.empty();
        validateAuthentication(authentication);
        ScopeType effectiveType = scopeType == null ? ScopeType.TENANT : scopeType;
        String effectiveScopeId = normalizeScopeId(authentication.activeTenant(), effectiveType, scopeId);
        AuthorizationRequest request = new AuthorizationRequest(authentication.principal(), authentication.activeTenant(),
                permission, resourceType, resourceId, effectiveType.name(), effectiveScopeId,
                authentication.securityEpoch(), requestContext == null ? Map.of() : Map.copyOf(requestContext));
        AuthorizationDecision decision = authorization.authorize(request);
        return Optional.of(shadow.get().compare(legacyDecision, request, decision, route));
    }

    /** Records compatibility evidence for an already evaluated target decision without re-authorizing. */
    public Optional<ShadowDecisionRecord> recordShadow(AuthenticationContext authentication,
                                                       LegacyDecision legacyDecision,
                                                       AuthorizationDecision decision,
                                                       String permission, String resourceType, String resourceId,
                                                       ScopeType scopeType, String scopeId, String route,
                                                       Map<String,String> requestContext) {
        if (shadow.isEmpty()) return Optional.empty();
        validateAuthentication(authentication);
        Objects.requireNonNull(decision, "decision");
        ScopeType effectiveType = scopeType == null
                ? (authentication.activeTenant().scope() == TenantRef.Scope.INSTANCE ? ScopeType.INSTANCE : ScopeType.TENANT)
                : scopeType;
        String effectiveScopeId = normalizeScopeId(authentication.activeTenant(), effectiveType, scopeId);
        AuthorizationRequest request = new AuthorizationRequest(authentication.principal(), authentication.activeTenant(),
                permission, resourceType, resourceId, effectiveType.name(), effectiveScopeId,
                authentication.securityEpoch(), requestContext == null ? Map.of() : Map.copyOf(requestContext));
        return Optional.of(shadow.get().compare(legacyDecision, request, decision, route));
    }

    private void validateAuthentication(AuthenticationContext authentication) {
        Objects.requireNonNull(authentication, "authentication");
        if (!authentication.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException("AUTH_SESSION_EXPIRED");
        }
        if (!authentication.subject().subjectId().equals(authentication.principal().principalId())
                && authentication.principal().principalType() != PrincipalRef.PrincipalType.GROUP) {
            throw new IllegalStateException("AUTH_PRINCIPAL_SUBJECT_MISMATCH");
        }
    }

    private String normalizeScopeId(TenantRef tenant, ScopeType type, String scopeId) {
        String value = scopeId == null ? "" : scopeId.trim();
        if (type == ScopeType.INSTANCE) {
            if (tenant.scope() != TenantRef.Scope.INSTANCE) throw new IllegalArgumentException("AUTH_TENANT_MISMATCH");
            return "INSTANCE";
        }
        if (tenant.scope() != TenantRef.Scope.TENANT) throw new IllegalArgumentException("AUTH_TENANT_MISMATCH");
        if (type == ScopeType.TENANT) return tenant.tenantId();
        if (value.isBlank()) throw new IllegalArgumentException("scopeId is required for " + type);
        return value;
    }
}
