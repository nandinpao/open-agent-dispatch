package com.opensocket.aievent.core.integration.handoff;

import java.time.OffsetDateTime;

/** Append-only evidence for Dispatch release and repair. */
public record HandoffReleaseEvidence(
        String tenantId,
        String evidenceId,
        String snapshotId,
        HandoffReleaseEvidenceType evidenceType,
        HandoffSnapshotReleaseStatus releaseStatus,
        HandoffReconciliationClassification classification,
        String dispatchEvidenceReference,
        String reasonCode,
        int attemptNo,
        String actorType,
        String actorId,
        String correlationId,
        OffsetDateTime occurredAt) {
}
