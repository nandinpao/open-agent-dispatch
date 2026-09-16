package com.opensocket.aievent.core.iam.rbac.domain.manifest;

public record PermissionManifestDriftSummary(
        String manifestId,
        long total,
        long matching,
        long sourceChanged,
        long descriptorChanged,
        long unregisteredSource,
        long staleManifest,
        long unknownPermission,
        long retiredPermission,
        long missingResolver,
        long blockers) {}
