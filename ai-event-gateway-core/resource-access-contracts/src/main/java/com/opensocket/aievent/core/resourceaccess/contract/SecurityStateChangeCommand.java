package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record SecurityStateChangeCommand(ResourceRef resourceRef,ResourceSecurityState targetState,long expectedResourceVersion,String actorId,String reason,String incidentId,String correlationId,String idempotencyKey,Instant requestedAt){
    public SecurityStateChangeCommand{Objects.requireNonNull(resourceRef,"resourceRef");Objects.requireNonNull(targetState,"targetState");if(expectedResourceVersion<1)throw new IllegalArgumentException("expectedResourceVersion must be positive");actorId=required(actorId,"actorId");reason=required(reason,"reason");incidentId=incidentId==null?"":incidentId.trim();correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
