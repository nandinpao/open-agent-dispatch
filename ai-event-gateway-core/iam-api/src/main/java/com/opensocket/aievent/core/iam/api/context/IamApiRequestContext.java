package com.opensocket.aievent.core.iam.api.context;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record IamApiRequestContext(Optional<AuthenticationContext> authentication, String correlationId,
                                   String idempotencyKey, String auditReason, String clientAddress,
                                   String userAgent, Instant requestedAt, Set<String> credentialPermissionBoundary) {
    public IamApiRequestContext(Optional<AuthenticationContext> authentication, String correlationId,
                                String idempotencyKey, String auditReason, String clientAddress,
                                String userAgent, Instant requestedAt) {
        this(authentication, correlationId, idempotencyKey, auditReason, clientAddress, userAgent, requestedAt, Set.of());
    }

    public IamApiRequestContext {
        authentication = authentication == null ? Optional.empty() : authentication;
        correlationId = requireText(correlationId, "correlationId", 128);
        idempotencyKey = normalize(idempotencyKey, 200);
        auditReason = normalize(auditReason, 500);
        clientAddress = normalize(clientAddress, 128);
        userAgent = normalize(userAgent, 1000);
        Objects.requireNonNull(requestedAt, "requestedAt");
        credentialPermissionBoundary = credentialPermissionBoundary == null ? Set.of() : Set.copyOf(credentialPermissionBoundary);
    }
    public AuthenticationContext requireAuthentication() {
        return authentication.orElseThrow(() -> IamApiException.unauthorized("AUTHENTICATION_REQUIRED", "Authentication is required"));
    }
    public String actorId() { return requireAuthentication().subject().subjectId(); }
    public boolean credentialBounded() { return !credentialPermissionBoundary.isEmpty(); }
    public void requireCredentialPermission(String permission) {
        if (credentialBounded() && !credentialPermissionBoundary.contains(permission)) {
            throw IamApiException.forbidden("AUTH_TOKEN_SCOPE_INSUFFICIENT", "The presented credential does not grant this Atomic Permission", permission);
        }
    }
    public String activeTenantId() {
        var tenant = requireAuthentication().activeTenant();
        if (tenant.scope() != com.opensocket.aievent.core.iam.security.contract.TenantRef.Scope.TENANT) {
            throw IamApiException.forbidden("AUTH_TENANT_REQUIRED", "An active Tenant is required", "");
        }
        return tenant.tenantId();
    }
    public long requireExpectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) throw IamApiException.preconditionRequired("IAM_IF_MATCH_REQUIRED", "If-Match is required");
        String value = ifMatch.trim();
        if (value.startsWith("W/")) value = value.substring(2);
        value = value.replace("\"", "");
        try { long parsed = Long.parseLong(value); if (parsed < 1) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException ex) { throw IamApiException.badRequest("IAM_IF_MATCH_INVALID", "If-Match must contain a positive numeric version"); }
    }
    public String requireIdempotencyKey() {
        if (idempotencyKey.isBlank()) throw IamApiException.badRequest("IAM_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
        return idempotencyKey;
    }
    public String requireAuditReason() {
        if (auditReason.isBlank()) throw IamApiException.badRequest("IAM_AUDIT_REASON_REQUIRED", "X-Audit-Reason is required for this sensitive action");
        return auditReason;
    }
    private static String requireText(String v,String f,int max){String x=normalize(v,max);if(x.isBlank())throw new IllegalArgumentException(f+" is required");return x;}
    private static String normalize(String v,int max){String x=v==null?"":v.trim();if(x.length()>max)throw new IllegalArgumentException("value exceeds "+max);return x;}
}
