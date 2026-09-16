package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CreateVisibilityPolicyCommand(String tenantId,String policyId,ResourceType resourceType,String policyName,VisibilityLevel maximumVisibility,SensitivityLevel maximumSensitivity,List<VisibilityFieldRule> fieldRules,String actorId,String correlationId,String idempotencyKey,Instant requestedAt){
    public CreateVisibilityPolicyCommand{tenantId=required(tenantId,"tenantId");policyId=required(policyId,"policyId");Objects.requireNonNull(resourceType,"resourceType");policyName=required(policyName,"policyName");Objects.requireNonNull(maximumVisibility,"maximumVisibility");Objects.requireNonNull(maximumSensitivity,"maximumSensitivity");fieldRules=fieldRules==null?List.of():List.copyOf(fieldRules);actorId=required(actorId,"actorId");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");Objects.requireNonNull(requestedAt,"requestedAt");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
