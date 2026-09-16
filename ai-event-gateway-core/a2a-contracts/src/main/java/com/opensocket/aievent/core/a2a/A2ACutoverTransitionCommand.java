package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

public record A2ACutoverTransitionCommand(
        String scopeId,
        long expectedVersion,
        A2ACutoverStage targetStage,
        long shadowMismatchCount,
        boolean issueTrackingEnabled,
        String migrationEvidenceReference,
        String runtimeGateRunId,
        String releaseEvidenceReference,
        OffsetDateTime rollbackDeadline,
        String actorId,
        String reason) {
}
