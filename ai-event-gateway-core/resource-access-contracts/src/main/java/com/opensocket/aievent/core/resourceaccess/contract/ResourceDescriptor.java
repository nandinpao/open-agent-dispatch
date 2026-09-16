package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Authoritative server-side projection. Controllers must never construct this from request payload fields. */
public record ResourceDescriptor(
        ResourceRef resourceRef,
        String resourceKey,
        OwnershipDescriptor ownership,
        ResourceRef parentResource,
        ResourceRef rootResource,
        VisibilityDescriptor visibility,
        ResourceSecurityState securityState,
        long participantVersion,
        long resourceVersion,
        DescriptorAuthority descriptorAuthority,
        String descriptorHash,
        Instant resolvedAt) {
    public ResourceDescriptor {
        Objects.requireNonNull(resourceRef, "resourceRef");
        resourceKey = resourceKey == null ? "" : resourceKey.trim();
        ownership = ownership == null ? OwnershipDescriptor.unowned(0) : ownership;
        validateSameTenant(resourceRef, parentResource, "parentResource");
        validateSameTenant(resourceRef, rootResource, "rootResource");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(securityState, "securityState");
        if (participantVersion < 0 || resourceVersion < 0) throw new IllegalArgumentException("descriptor versions must be non-negative");
        Objects.requireNonNull(descriptorAuthority, "descriptorAuthority");
        descriptorHash = requireText(descriptorHash, "descriptorHash");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }
    private static void validateSameTenant(ResourceRef source, ResourceRef candidate, String field) {
        if (candidate != null && !source.tenantId().equals(candidate.tenantId())) throw new IllegalArgumentException(field + " must use the same tenant");
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
