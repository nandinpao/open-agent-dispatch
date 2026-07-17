package com.opensocket.aievent.core.enforce;

import java.time.OffsetDateTime;

public record EnforceArtifactRetentionRecord(
        String artifactName,
        String artifactPath,
        OffsetDateTime generatedAt,
        OffsetDateTime retainedUntil,
        String source) {
}
