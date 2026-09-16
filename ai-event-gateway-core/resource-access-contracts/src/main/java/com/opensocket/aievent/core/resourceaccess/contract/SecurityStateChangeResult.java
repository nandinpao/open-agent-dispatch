package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record SecurityStateChangeResult(ResourceRef resourceRef,ResourceSecurityState previousState,ResourceSecurityState currentState,long resultingResourceVersion,long resultingSecurityEpoch,String actorId,String reason,Instant changedAt){
    public SecurityStateChangeResult{Objects.requireNonNull(resourceRef,"resourceRef");Objects.requireNonNull(previousState,"previousState");Objects.requireNonNull(currentState,"currentState");if(resultingResourceVersion<1||resultingSecurityEpoch<1)throw new IllegalArgumentException("result versions must be positive");actorId=required(actorId,"actorId");reason=required(reason,"reason");Objects.requireNonNull(changedAt,"changedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
