package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record CreateScopeGrantCommand(
        String tenantId, String grantId, ScopePrincipalType principalType, String principalId,
        String permissionCode, ResourceType resourceType, ScopeType scopeType, String scopeRefId,
        VisibilityLevel visibilityLevel, Instant validFrom, Instant validTo, ScopeGrantSource grantSource,
        String grantReason, String actorId, String correlationId, String idempotencyKey, Instant requestedAt) {
    public CreateScopeGrantCommand {
        tenantId=required(tenantId,"tenantId");grantId=required(grantId,"grantId");Objects.requireNonNull(principalType,"principalType");principalId=required(principalId,"principalId");
        permissionCode=required(permissionCode,"permissionCode");Objects.requireNonNull(resourceType,"resourceType");Objects.requireNonNull(scopeType,"scopeType");
        scopeRefId=scopeRefId==null?"":scopeRefId.trim();Objects.requireNonNull(visibilityLevel,"visibilityLevel");Objects.requireNonNull(validFrom,"validFrom");
        if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");Objects.requireNonNull(grantSource,"grantSource");
        grantReason=required(grantReason,"grantReason");actorId=required(actorId,"actorId");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
