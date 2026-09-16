package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record CreateExplicitDenyCommand(
        String tenantId,String denyId,ScopePrincipalType principalType,String principalId,String permissionCode,
        ResourceType resourceType,ScopeType scopeType,String scopeRefId,String denyReason,DenySeverity severity,
        Instant validFrom,Instant validTo,String actorId,String correlationId,String idempotencyKey,Instant requestedAt){
    public CreateExplicitDenyCommand{tenantId=required(tenantId,"tenantId");denyId=required(denyId,"denyId");Objects.requireNonNull(principalType,"principalType");principalId=required(principalId,"principalId");permissionCode=permissionCode==null?"":permissionCode.trim();Objects.requireNonNull(resourceType,"resourceType");Objects.requireNonNull(scopeType,"scopeType");scopeRefId=scopeRefId==null?"":scopeRefId.trim();denyReason=required(denyReason,"denyReason");Objects.requireNonNull(severity,"severity");Objects.requireNonNull(validFrom,"validFrom");if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");actorId=required(actorId,"actorId");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
