package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.time.OffsetDateTime;

public record RuntimeConfigurationNodeApplyState(
        String configSetId,
        String nodeId,
        String nodeRole,
        String nodeInstanceId,
        String desiredRevisionId,
        String appliedRevisionId,
        RuntimeConfigurationApplyStatus state,
        String snapshotFingerprint,
        OffsetDateTime desiredAt,
        OffsetDateTime distributedAt,
        OffsetDateTime appliedAt,
        OffsetDateTime lastSeenAt,
        String errorCode,
        String errorDetail,
        String authorityRuntimeState,
        OffsetDateTime snapshotExpiresAt,
        OffsetDateTime authorityObservedAt) {
}
