package com.opensocket.aievent.core.resourceaccess.contract;

/** Server-resolved sensitivity and field-visibility boundary. */
public record VisibilityDescriptor(
        SensitivityLevel sensitivityLevel,
        VisibilityLevel maximumVisibility,
        String visibilityPolicyId,
        PolicyVersion policyVersion) {
    public VisibilityDescriptor {
        if (sensitivityLevel == null) throw new IllegalArgumentException("sensitivityLevel is required");
        if (maximumVisibility == null) throw new IllegalArgumentException("maximumVisibility is required");
        visibilityPolicyId = visibilityPolicyId == null ? "" : visibilityPolicyId.trim();
        policyVersion = policyVersion == null ? PolicyVersion.ZERO : policyVersion;
    }
}
