package com.opensocket.aievent.core.task.lineage;

import java.time.OffsetDateTime;

/**
 * Stage 2 relational projection of parent/child Task lineage.
 * Existing Task rootTaskId/parentTaskId remain authoritative during A0-R2.
 */
public record TaskLineageEdge(
        String tenantId,
        String rootTaskId,
        String parentTaskId,
        String childTaskId,
        int depth,
        String creationReason,
        String createdByPrincipalRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
