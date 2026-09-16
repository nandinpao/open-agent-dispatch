package com.opensocket.aievent.core.iam.rbac.domain.manifest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record PermissionCoverageEvidence(
        String evidenceId,
        String manifestId,
        String evidenceType,
        String manifestHash,
        String catalogRevisionId,
        String catalogContentHash,
        int entryCount,
        int coveredEntryCount,
        BigDecimal coveragePercent,
        String sourceInventoryRevision,
        String detailsJson,
        String actorId,
        Optional<String> correlationId,
        Instant occurredAt) {}
