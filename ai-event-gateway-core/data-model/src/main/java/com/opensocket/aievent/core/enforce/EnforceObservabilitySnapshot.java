package com.opensocket.aievent.core.enforce;

import java.time.OffsetDateTime;

public record EnforceObservabilitySnapshot(
        OffsetDateTime generatedAt,
        String source,
        String mode,
        String window,
        long v2Allowed,
        long v2Blocked,
        long noCandidate,
        long fallbackDenied,
        long qualityUnavailable,
        long scoreBreakdownMissing,
        double blockedRate,
        double noCandidateRate,
        double qualityUnavailableRate,
        String latestAcceptanceArtifact,
        String latestReadinessArtifact,
        String latestArchiveManifest,
        long readinessBlockingCount) {
}
