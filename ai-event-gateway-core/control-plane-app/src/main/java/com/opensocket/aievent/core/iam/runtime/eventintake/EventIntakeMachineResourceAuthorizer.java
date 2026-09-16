package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
import com.opensocket.aievent.core.iam.security.contract.*;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import com.opensocket.aievent.core.iam.token.domain.CidrBlock;
import com.opensocket.aievent.core.iam.token.domain.MachineTokenClaims;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccountId;
import com.opensocket.aievent.core.security.incident.RuntimeIncidentControlPolicy;
import org.springframework.dao.DataAccessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Phase 9 resource-server authority: signed JWT boundary + current IAM authority, never token claims alone. */
public final class EventIntakeMachineResourceAuthorizer {
    private final MachineJwtApplicationService jwt;
    private final TenantRbacExecutionPort tenants;
    private final TokenPermissionAuthorityPort authority;
    private final TokenSecurityEpochPort epochs;
    private final ServiceAccountRepository accounts;
    private final TokenRateLimitPort rates;
    private final EventIntakeSecurityProperties properties;
    private final Clock clock;
    private final RuntimeIncidentControlPolicy incidentControls;

    public EventIntakeMachineResourceAuthorizer(MachineJwtApplicationService jwt, TenantRbacExecutionPort tenants,
            TokenPermissionAuthorityPort authority, TokenSecurityEpochPort epochs, ServiceAccountRepository accounts,
            TokenRateLimitPort rates, EventIntakeSecurityProperties properties, Clock clock, RuntimeIncidentControlPolicy incidentControls) {
        this.jwt=jwt; this.tenants=tenants; this.authority=authority; this.epochs=epochs; this.accounts=accounts; this.rates=rates; this.properties=properties; this.clock=clock; this.incidentControls=incidentControls;
    }

    public MachineAuthenticationContext authorize(String compactToken, String apiPath, String sourceIp) {
        final MachineJwtApplicationService machineJwt = jwt;
        final var decoded = verifyCryptographic(compactToken);
        final MachineTokenClaims claims = decoded.claims();
        if (!"SERVICE_ACCOUNT".equals(claims.principalType())) deny401("MACHINE_EVENT_INTAKE_TOKEN_INVALID", "Unsupported machine principal type.");
        if (!claims.scopes().contains(properties.getRequiredScope())) deny403("MACHINE_EVENT_INTAKE_SCOPE_DENIED", "Machine token does not grant Event Intake scope.");
        if (!claims.audiences().contains(properties.getAudience())) deny403("MACHINE_EVENT_INTAKE_AUDIENCE_DENIED", "Machine token audience is not valid for Event Intake.");
        if (properties.isRequireApiPrefix() && (claims.apiPrefixes().isEmpty() || claims.apiPrefixes().stream().noneMatch(apiPath::startsWith))) {
            deny403("MACHINE_EVENT_INTAKE_API_PREFIX_DENIED", "Machine token API boundary does not allow Event Intake.");
        }
        if (!permitsIp(claims.cidrs(), sourceIp)) deny403("MACHINE_EVENT_INTAKE_CIDR_DENIED", "Machine token CIDR boundary denied the caller.");

        PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, claims.subject());
        return tenants.write(claims.tenantId(), "machine-resource:" + claims.subject(), () -> {
            try {
                if (incidentControls.machineAuthenticationBlocked(claims.tenantId(), claims.subject(), claims.credentialId()))
                    deny401("MACHINE_EVENT_INTAKE_INCIDENT_SUSPENDED", "Machine credential is suspended by an active Security Incident control.");
                authority.requireActivePrincipal(claims.tenantId(), principal);
                SecurityEpoch current = epochs.current(claims.tenantId(), principal);
                if (!claims.hasCurrentSecurityEpoch(current)) deny401("MACHINE_EVENT_INTAKE_TOKEN_STALE", "Machine token security epoch is stale.");
                Set<String> effectivePermissions = authority.effectivePermissions(claims.tenantId(), principal);
                if (effectivePermissions.isEmpty()) deny403("MACHINE_EVENT_INTAKE_RBAC_DENIED", "Service Account has no current RBAC authority.");
                var account = accounts.find(claims.tenantId(), new ServiceAccountId(claims.subject())).orElseThrow(
                        () -> EventIntakeResourceAuthorizationException.unauthorized("MACHINE_EVENT_INTAKE_TOKEN_INVALID", "Machine principal is not available."));
                // JWT claims are the issuance-time ceiling, not a durable authorization snapshot.
                // Re-evaluate the current Service Account boundary so narrowing access takes effect
                // immediately even when an already-issued short-lived token has not expired yet.
                if (!account.machineScopes().contains(properties.getRequiredScope()))
                    deny403("MACHINE_EVENT_INTAKE_SCOPE_DENIED", "Current Service Account scope no longer allows Event Intake.");
                if (!account.restrictions().permitsAudience(properties.getAudience()))
                    deny403("MACHINE_EVENT_INTAKE_AUDIENCE_DENIED", "Current Service Account audience no longer allows Event Intake.");
                if (properties.isRequireApiPrefix() && !account.restrictions().permitsApiPath(apiPath))
                    deny403("MACHINE_EVENT_INTAKE_API_PREFIX_DENIED", "Current Service Account API boundary no longer allows Event Intake.");
                if (!account.restrictions().permitsIp(sourceIp))
                    deny403("MACHINE_EVENT_INTAKE_CIDR_DENIED", "Current Service Account CIDR boundary denied the caller.");
                Set<String> currentScopes = intersection(claims.scopes(), account.machineScopes());
                Set<String> currentSourceSystems = intersection(claims.sourceSystems(), account.allowedSourceSystems());
                Instant now = clock.instant();
                int effectiveRateLimit=incidentControls.effectiveMachineRateLimit(claims.tenantId(),claims.subject(),claims.credentialId(),account.rateLimitPerMinute());
                if (!rates.tryAcquire(claims.tenantId(), "event-intake:"+claims.subject()+":"+claims.credentialId(), effectiveRateLimit, now)) throw EventIntakeResourceAuthorizationException.rateLimited();
                MachinePrincipal machinePrincipal = new MachinePrincipal(claims.subject(), MachinePrincipalType.SERVICE_ACCOUNT, TenantRef.tenant(claims.tenantId()));
                MachineAccessBoundary boundary = new MachineAccessBoundary(effectivePermissions, currentScopes, claims.audiences(), currentSourceSystems, claims.apiPrefixes(), claims.cidrs(), Map.of());
                AuthenticationAssurance assurance = new AuthenticationAssurance(AuthenticationAssurance.Level.SYSTEM, Set.of("OAUTH2_BEARER_JWT"), now);
                return new MachineAuthenticationContext(machinePrincipal,
                        new MachineCredentialRef(claims.credentialId(), MachineCredentialRef.CredentialType.ACCESS_TOKEN, claims.jwtId(), claims.issuer(), claims.clientId()),
                        boundary, assurance, current, claims.issuedAt(), claims.expiresAt());
            } catch (EventIntakeResourceAuthorizationException e) {
                throw e;
            } catch (DataAccessException e) {
                throw EventIntakeResourceAuthorizationException.unavailable(
                        "MACHINE_EVENT_INTAKE_AUTHORITY_UNAVAILABLE",
                        "Machine authorization state could not be evaluated.");
            } catch (RuntimeException e) {
                throw EventIntakeResourceAuthorizationException.unauthorized(
                        "MACHINE_EVENT_INTAKE_TOKEN_INVALID",
                        "Machine access token validation failed.");
            }
        });
    }

    private MachineJwtCodecPort.DecodedToken verifyCryptographic(String token) {
        try { return jwt.verify(token); }
        catch (RuntimeException e) { throw EventIntakeResourceAuthorizationException.unauthorized("MACHINE_EVENT_INTAKE_TOKEN_INVALID", "Machine access token validation failed."); }
    }

    private static Set<String> intersection(Set<String> signed, Set<String> current) {
        if (signed == null || current == null || signed.isEmpty() || current.isEmpty()) return Set.of();
        java.util.TreeSet<String> out = new java.util.TreeSet<>(signed);
        out.retainAll(current);
        return Set.copyOf(out);
    }

    private static boolean permitsIp(Set<String> cidrs, String sourceIp) {
        if (cidrs == null || cidrs.isEmpty()) return true;
        if (sourceIp == null || sourceIp.isBlank()) return false;
        try { return cidrs.stream().map(CidrBlock::parse).anyMatch(c -> c.contains(sourceIp)); }
        catch (RuntimeException e) { return false; }
    }
    private static void deny401(String code,String message){throw EventIntakeResourceAuthorizationException.unauthorized(code,message);}
    private static void deny403(String code,String message){throw EventIntakeResourceAuthorizationException.forbidden(code,message);}
}
