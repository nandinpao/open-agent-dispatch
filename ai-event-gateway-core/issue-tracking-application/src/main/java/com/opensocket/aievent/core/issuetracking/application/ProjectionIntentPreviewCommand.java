package com.opensocket.aievent.core.issuetracking.application;

import com.opensocket.aievent.core.issuetracking.contract.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Explicit input used to preview one canonical Projection Intent. */
public record ProjectionIntentPreviewCommand(
        String tenantId,
        String taskId,
        String taskType,
        String sourceSystem,
        String connectionId,
        String projectMappingId,
        ProjectionPurpose projectionPurpose,
        long mappingVersion,
        String mappingSchemaHash,
        String summary,
        String description,
        String issueType,
        String priority,
        List<String> labels,
        List<String> components,
        Map<String,Object> approvedContext,
        List<ExternalIssueComment> comments,
        List<ExternalIssueRelation> links,
        Map<String,String> sourceEvidence,
        String domainEventId,
        long operationSequence,
        long expectedProjectionVersion,
        String correlationId,
        OffsetDateTime createdAt) { }
