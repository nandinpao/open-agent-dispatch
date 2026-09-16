package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PermissionManifestStatisticsRequest(
        @Min(1) int entryCount,
        @Min(0) int protectedEntryCount,
        @Min(0) int coveredEntryCount,
        @NotNull BigDecimal coveragePercent,
        @Min(0) int targetPermissionCount,
        @Min(0) int legacyAuthorityCount,
        @Min(0) int exemptCount,
        @Min(0) int delegatedCount,
        @Min(0) int uncoveredCount) {}
