package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record ExternalIssueConflict(
 String tenantId,String conflictId,String connectionId,String externalProjectId,String externalIssueId,String taskIssueLinkId,
 String projectionId,String observationId,ExternalIssueConflictClassification classification,ConflictResolutionPolicy resolutionPolicy,
 ExternalIssueConflictStatus status,String desiredDocumentHash,String observedDocumentHash,String stateDiffJson,
 String evidenceHash,String reasonCode,OffsetDateTime detectedAt,OffsetDateTime decisionAt,OffsetDateTime resolvedAt,
 String resolvedBy,String resolutionReason,String resolutionIdempotencyKey,String resolutionRequestHash,long version,
 String correlationId) {}
