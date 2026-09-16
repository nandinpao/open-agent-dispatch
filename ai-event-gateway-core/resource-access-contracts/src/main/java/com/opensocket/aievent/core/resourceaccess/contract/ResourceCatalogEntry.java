package com.opensocket.aievent.core.resourceaccess.contract;

/** Immutable catalog metadata; it describes authority and capabilities but grants no access. */
public record ResourceCatalogEntry(
        ResourceType resourceType,
        ResourceCategory category,
        DescriptorAuthority descriptorAuthority,
        boolean ownershipSupported,
        boolean participantsSupported,
        boolean fieldVisibilitySupported,
        boolean runtimeLeaseSupported,
        SensitivityLevel defaultSensitivity,
        String description) {
    public ResourceCatalogEntry {
        if (resourceType == null || category == null || descriptorAuthority == null || defaultSensitivity == null) throw new IllegalArgumentException("catalog identity is required");
        description = description == null ? "" : description.trim();
    }
}
