package com.opensocket.aievent.core.iam.rbac.domain.manifest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record ApplicationPermissionManifest(
        String manifestId,
        String applicationId,
        String environment,
        String buildVersion,
        String manifestRevision,
        int schemaVersion,
        String manifestHash,
        String catalogRevisionId,
        String catalogRevisionCode,
        String catalogContentHash,
        String sourceInventoryRevision,
        int entryCount,
        int protectedEntryCount,
        int coveredEntryCount,
        BigDecimal coveragePercent,
        int targetPermissionCount,
        int legacyAuthorityCount,
        int exemptCount,
        int delegatedCount,
        int uncoveredCount,
        String status,
        Instant registeredAt,
        String registeredBy,
        Optional<Instant> activatedAt,
        Optional<String> activatedBy,
        long version,
        long matchingEntries,
        long sourceChanged,
        long descriptorChanged,
        long unregisteredSource,
        long staleManifest,
        long unknownPermission,
        long retiredPermission,
        long missingResolver,
        long runtimeDriftBlockers) {}
