package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record GrantPrincipalClearanceCommand(String tenantId,String clearanceId,ScopePrincipalType principalType,String principalId,SensitivityLevel clearanceLevel,Instant validFrom,Instant validTo,String actorId,String reason,String correlationId,String idempotencyKey,Instant requestedAt){
    public GrantPrincipalClearanceCommand{tenantId=required(tenantId,"tenantId");clearanceId=required(clearanceId,"clearanceId");Objects.requireNonNull(principalType,"principalType");principalId=required(principalId,"principalId");Objects.requireNonNull(clearanceLevel,"clearanceLevel");Objects.requireNonNull(validFrom,"validFrom");if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");actorId=required(actorId,"actorId");reason=required(reason,"reason");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
