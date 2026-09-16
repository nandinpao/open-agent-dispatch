package com.opensocket.aievent.core.resourceaccess.api;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** P4RA-C management surface. Disabled by default and does not evaluate business authorization. */
@RestController
@RequestMapping("/api/resource-access")
@ConditionalOnProperty(prefix="resource-access",name={"enabled","management-api-enabled"},havingValue="true")
public final class ResourceScopeAdministrationController {
    private final ResourceScopeGrantService grants;
    private final ResourceExplicitDenyService denies;
    private final ResourceVisibilityPolicyService visibility;
    private final PrincipalClearanceService clearances;
    private final ResourceSecurityStateService securityStates;
    private final ResourceAccessApiContextPort context;
    public ResourceScopeAdministrationController(ResourceScopeGrantService grants,ResourceExplicitDenyService denies,
            ResourceVisibilityPolicyService visibility,PrincipalClearanceService clearances,
            ResourceSecurityStateService securityStates,ResourceAccessApiContextPort context){
        this.grants=grants;this.denies=denies;this.visibility=visibility;this.clearances=clearances;this.securityStates=securityStates;this.context=context;
    }

    @GetMapping("/grants/{grantId}")
    public ScopeGrantRecord grant(@PathVariable String grantId){var c=context.current();return grants.find(c.tenantId(),grantId);}
    @PostMapping("/grants")
    public ScopeGrantRecord createGrant(@RequestBody CreateGrantBody body,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return grants.create(new CreateScopeGrantCommand(c.tenantId(),body.grantId(),body.principalType(),body.principalId(),body.permissionCode(),body.resourceType(),body.scopeType(),body.scopeRefId(),body.visibilityLevel(),body.validFrom(),body.validTo(),body.grantSource(),body.grantReason(),c.actorId(),c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/grants/{grantId}/submit")
    public ScopeGrantRecord submitGrant(@PathVariable String grantId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return grants.submit(mutation(grantId,version,idem,reason));}
    @PostMapping("/grants/{grantId}/approve")
    public ScopeGrantRecord approveGrant(@PathVariable String grantId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return grants.approve(mutation(grantId,version,idem,reason));}
    @PostMapping("/grants/{grantId}/suspend")
    public ScopeGrantRecord suspendGrant(@PathVariable String grantId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return grants.suspend(mutation(grantId,version,idem,reason));}
    @PostMapping("/grants/{grantId}/revoke")
    public ScopeGrantRecord revokeGrant(@PathVariable String grantId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return grants.revoke(mutation(grantId,version,idem,reason));}

    @GetMapping("/denies/{denyId}")
    public ExplicitDenyRecord deny(@PathVariable String denyId){var c=context.current();return denies.find(c.tenantId(),denyId);}
    @PostMapping("/denies")
    public ExplicitDenyRecord createDeny(@RequestBody CreateDenyBody body,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return denies.create(new CreateExplicitDenyCommand(c.tenantId(),body.denyId(),body.principalType(),body.principalId(),body.permissionCode(),body.resourceType(),body.scopeType(),body.scopeRefId(),body.denyReason(),body.severity(),body.validFrom(),body.validTo(),c.actorId(),c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/denies/{denyId}/submit")
    public ExplicitDenyRecord submitDeny(@PathVariable String denyId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return denies.submit(new ExplicitDenyMutationCommand(c.tenantId(),denyId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/denies/{denyId}/approve")
    public ExplicitDenyRecord approveDeny(@PathVariable String denyId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return denies.approve(new ExplicitDenyMutationCommand(c.tenantId(),denyId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/denies/{denyId}/revoke")
    public ExplicitDenyRecord revokeDeny(@PathVariable String denyId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return denies.revoke(new ExplicitDenyMutationCommand(c.tenantId(),denyId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}

    @GetMapping("/visibility-policies/{policyId}")
    public VisibilityPolicyRecord policy(@PathVariable String policyId){var c=context.current();return visibility.find(c.tenantId(),policyId);}
    @PostMapping("/visibility-policies")
    public VisibilityPolicyRecord createPolicy(@RequestBody CreateVisibilityPolicyBody body,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return visibility.create(new CreateVisibilityPolicyCommand(c.tenantId(),body.policyId(),body.resourceType(),body.policyName(),body.maximumVisibility(),body.maximumSensitivity(),body.fieldRules(),c.actorId(),c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/visibility-policies/{policyId}/activate")
    public VisibilityPolicyRecord activatePolicy(@PathVariable String policyId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return visibility.activate(policyMutation(policyId,version,idem,reason));}
    @PostMapping("/visibility-policies/{policyId}/retire")
    public VisibilityPolicyRecord retirePolicy(@PathVariable String policyId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){return visibility.retire(policyMutation(policyId,version,idem,reason));}

    @GetMapping("/clearances/{clearanceId}")
    public PrincipalClearanceRecord clearance(@PathVariable String clearanceId){var c=context.current();return clearances.find(c.tenantId(),clearanceId);}
    @PostMapping("/clearances")
    public PrincipalClearanceRecord grantClearance(@RequestBody GrantClearanceBody body,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return clearances.create(new GrantPrincipalClearanceCommand(c.tenantId(),body.clearanceId(),body.principalType(),body.principalId(),body.clearanceLevel(),body.validFrom(),body.validTo(),c.actorId(),body.reason(),c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/clearances/{clearanceId}/submit")
    public PrincipalClearanceRecord submitClearance(@PathVariable String clearanceId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return clearances.submit(new ClearanceMutationCommand(c.tenantId(),clearanceId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/clearances/{clearanceId}/approve")
    public PrincipalClearanceRecord approveClearance(@PathVariable String clearanceId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return clearances.approve(new ClearanceMutationCommand(c.tenantId(),clearanceId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}
    @PostMapping("/clearances/{clearanceId}/revoke")
    public PrincipalClearanceRecord revokeClearance(@PathVariable String clearanceId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return clearances.revoke(new ClearanceMutationCommand(c.tenantId(),clearanceId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt()));}

    @PostMapping("/resources/{resourceType}/{resourceId}/security-state")
    public SecurityStateChangeResult changeSecurityState(@PathVariable ResourceType resourceType,@PathVariable String resourceId,@RequestBody SecurityStateBody body,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return securityStates.change(new SecurityStateChangeCommand(new ResourceRef(c.tenantId(),resourceType,resourceId),body.targetState(),parseVersion(version),c.actorId(),reason,body.incidentId(),c.correlationId(),idem,c.requestedAt()));}

    private ScopeGrantMutationCommand mutation(String grantId,String version,String idem,String reason){var c=context.current();return new ScopeGrantMutationCommand(c.tenantId(),grantId,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt());}
    private VisibilityPolicyMutationCommand policyMutation(String id,String version,String idem,String reason){var c=context.current();return new VisibilityPolicyMutationCommand(c.tenantId(),id,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt());}
    private static long parseVersion(String value){String normalized=value==null?"":value.trim().replace("\"","");if(normalized.startsWith("W/"))normalized=normalized.substring(2);try{return Long.parseLong(normalized);}catch(NumberFormatException ex){throw new IllegalArgumentException("If-Match must contain the numeric resource version");}}

    public record CreateGrantBody(String grantId,ScopePrincipalType principalType,String principalId,String permissionCode,ResourceType resourceType,ScopeType scopeType,String scopeRefId,VisibilityLevel visibilityLevel,Instant validFrom,Instant validTo,ScopeGrantSource grantSource,String grantReason){}
    public record CreateDenyBody(String denyId,ScopePrincipalType principalType,String principalId,String permissionCode,ResourceType resourceType,ScopeType scopeType,String scopeRefId,String denyReason,DenySeverity severity,Instant validFrom,Instant validTo){}
    public record CreateVisibilityPolicyBody(String policyId,ResourceType resourceType,String policyName,VisibilityLevel maximumVisibility,SensitivityLevel maximumSensitivity,List<VisibilityFieldRule> fieldRules){}
    public record GrantClearanceBody(String clearanceId,ScopePrincipalType principalType,String principalId,SensitivityLevel clearanceLevel,Instant validFrom,Instant validTo,String reason){}
    public record SecurityStateBody(ResourceSecurityState targetState,String incidentId){}
}
