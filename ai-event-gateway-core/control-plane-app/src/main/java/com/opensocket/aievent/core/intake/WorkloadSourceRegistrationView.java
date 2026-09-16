package com.opensocket.aievent.core.intake;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkloadSourceRegistrationView(
        String tenantId,
        String sourceRegistrationId,
        String sourceSystemId,
        String registrationName,
        String channelType,
        String principalBindingMode,
        List<String> allowedPrincipalTypes,
        String staticPrincipalRef,
        String ownerDepartmentId,
        String ownerGroupId,
        List<String> allowedEventTypes,
        List<String> allowedObjectTypes,
        List<String> inputSchemas,
        String idempotencyStrategy,
        long idempotencyRetentionSeconds,
        String orderingStrategy,
        String acknowledgementMode,
        Integer rateLimitPerMinute,
        Long quotaPerDay,
        String dataClassificationProfile,
        String residencyProfile,
        String status,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        boolean defaultRegistration,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
