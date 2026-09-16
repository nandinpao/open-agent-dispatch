package com.opensocket.aievent.core.iam.rbac.application.command.manifest;

import com.opensocket.aievent.core.iam.rbac.domain.manifest.PermissionManifestRegistrationEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RegisterApplicationPermissionManifestCommand(
        String manifestId,
        String applicationId,
        String environment,
        String buildVersion,
        String manifestRevision,
        int schemaVersion,
        String manifestHash,
        String catalogRevisionId,
        String catalogRevisionCode,
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
        List<PermissionManifestRegistrationEntry> entries,
        String actorId,
        String correlationId,
        String auditReason,
        Instant requestedAt) {}
