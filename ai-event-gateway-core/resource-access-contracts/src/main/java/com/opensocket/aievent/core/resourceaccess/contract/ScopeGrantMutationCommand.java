package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record ScopeGrantMutationCommand(String tenantId,String grantId,long expectedVersion,String actorId,String reason,String correlationId,String idempotencyKey,Instant requestedAt){
    public ScopeGrantMutationCommand{tenantId=required(tenantId,"tenantId");grantId=required(grantId,"grantId");if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");actorId=required(actorId,"actorId");reason=required(reason,"reason");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
