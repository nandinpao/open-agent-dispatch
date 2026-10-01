package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.time.OffsetDateTime;

public record RuntimeConfigurationDistributionOutboxEntry(
        String configSetId,
        String revisionId,
        String environment,
        RuntimeConfigurationOutboxStatus status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        String leaseOwner,
        OffsetDateTime leaseUntil,
        String payloadFingerprint,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime distributedAt) {
}
