package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record ProjectionReadbackEvidence(
 String tenantId,String evidenceId,String outboxId,String projectionId,String externalIdempotencyMarker,
 ProjectionReadbackStatus status,String externalIssueId,String externalIssueKey,String observedFingerprint,
 String reasonCode,String safeSummary,OffsetDateTime observedAt,String correlationId) {}
