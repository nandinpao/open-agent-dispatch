package com.opensocket.aievent.core.issue.observability;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Canonical, evidence-backed Route B observability read model.
 *
 * <p>The first failed stage is selected by Core, never inferred by the Admin UI. A blank
 * firstFailedStage means no BLOCKED/FAILED stage currently exists.</p>
 */
public record IssueRuntimeJourneyView(
        String tenantId,
        String taskId,
        String correlationId,
        String overallStatus,
        IssueRuntimeStageCode currentStage,
        IssueRuntimeStageCode firstFailedStage,
        String reasonCode,
        String summary,
        long revision,
        OffsetDateTime generatedAt,
        List<IssueRuntimeStage> stages) {}
