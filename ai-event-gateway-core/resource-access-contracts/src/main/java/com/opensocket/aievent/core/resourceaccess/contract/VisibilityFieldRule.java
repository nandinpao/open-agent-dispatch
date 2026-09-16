package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

public record VisibilityFieldRule(
        String fieldRuleId, String fieldPath, VisibilityLevel minimumVisibilityLevel,
        SensitivityLevel sensitivityLevel, MaskingMethod maskingMethod,
        boolean exportAllowed, boolean downloadAllowed, int priority, long version) {
    public VisibilityFieldRule {
        fieldRuleId=required(fieldRuleId,"fieldRuleId");fieldPath=required(fieldPath,"fieldPath");
        Objects.requireNonNull(minimumVisibilityLevel,"minimumVisibilityLevel");Objects.requireNonNull(sensitivityLevel,"sensitivityLevel");
        Objects.requireNonNull(maskingMethod,"maskingMethod");if(priority<0)throw new IllegalArgumentException("priority must be non-negative");
        if(version<1)throw new IllegalArgumentException("version must be positive");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
