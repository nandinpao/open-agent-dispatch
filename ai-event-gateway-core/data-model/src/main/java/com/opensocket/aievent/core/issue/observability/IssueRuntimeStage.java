package com.opensocket.aievent.core.issue.observability;

import java.time.OffsetDateTime;
import java.util.List;

/** One deterministic stage in the canonical Issue Runtime Journey. */
public record IssueRuntimeStage(
        IssueRuntimeStageCode stage,
        IssueRuntimeStageStatus status,
        boolean required,
        boolean retryable,
        String reasonCode,
        String summary,
        OffsetDateTime changedAt,
        OffsetDateTime retryAfter,
        List<IssueRuntimeEvidenceRef> evidence) {}
