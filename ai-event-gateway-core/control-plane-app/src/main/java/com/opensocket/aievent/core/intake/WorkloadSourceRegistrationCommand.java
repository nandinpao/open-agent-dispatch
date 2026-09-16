package com.opensocket.aievent.core.intake;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkloadSourceRegistrationCommand(
        String sourceRegistrationId,
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
        Long idempotencyRetentionSeconds,
        String orderingStrategy,
        String acknowledgementMode,
        Integer rateLimitPerMinute,
        Long quotaPerDay,
        String dataClassificationProfile,
        String residencyProfile,
        String status,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        Boolean defaultRegistration) {
    public static WorkloadSourceRegistrationCommand empty() {
        return new WorkloadSourceRegistrationCommand(
                null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }
}
