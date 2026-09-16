package com.opensocket.aievent.core.iam.rbac.domain.manifest;

import java.util.List;
import java.util.Optional;

public record ApplicationPermissionManifestEntry(
        String manifestId,
        String entryPointId,
        String entryPointType,
        String ownerModule,
        String displayName,
        Optional<String> routePattern,
        Optional<String> httpMethod,
        String authorityState,
        String protectionMode,
        String coverageStatus,
        Optional<String> permissionCode,
        List<String> legacyAuthorities,
        String resourceType,
        String resourceResolverId,
        boolean scopeRequired,
        Optional<String> exemptionReason,
        String sourceRef,
        String sourceHash,
        String descriptorHash,
        String driftStatus,
        boolean blocker) {}
