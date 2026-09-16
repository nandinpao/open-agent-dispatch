package com.opensocket.aievent.core.resourceaccess.api;

import java.time.Instant;

/** Trusted request context resolved from the authenticated session; request bodies never choose tenant or actor. */
public record ResourceAccessApiRequestContext(String tenantId,String actorId,String correlationId,Instant requestedAt){
    public ResourceAccessApiRequestContext{tenantId=required(tenantId,"tenantId");actorId=required(actorId,"actorId");correlationId=required(correlationId,"correlationId");if(requestedAt==null)throw new IllegalArgumentException("requestedAt is required");}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
