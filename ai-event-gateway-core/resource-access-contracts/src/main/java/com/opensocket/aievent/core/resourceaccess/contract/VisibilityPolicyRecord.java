package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record VisibilityPolicyRecord(
        String tenantId, String policyId, ResourceType resourceType, String policyName,
        VisibilityLevel maximumVisibility, SensitivityLevel maximumSensitivity,
        VisibilityPolicyState state, List<VisibilityFieldRule> fieldRules,
        long version, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public VisibilityPolicyRecord {
        tenantId=required(tenantId,"tenantId");policyId=required(policyId,"policyId");Objects.requireNonNull(resourceType,"resourceType");
        policyName=required(policyName,"policyName");Objects.requireNonNull(maximumVisibility,"maximumVisibility");Objects.requireNonNull(maximumSensitivity,"maximumSensitivity");
        Objects.requireNonNull(state,"state");fieldRules=fieldRules==null?List.of():List.copyOf(fieldRules);if(version<1)throw new IllegalArgumentException("version must be positive");
        createdBy=required(createdBy,"createdBy");updatedBy=required(updatedBy,"updatedBy");Objects.requireNonNull(createdAt,"createdAt");Objects.requireNonNull(updatedAt,"updatedAt");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
