package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationConflict(String tenantId,String conflictId,String taskId,String taskIssueLinkId,String inboxId,String conflictType,
 String taskStatus,String externalIssueStatus,String detailJson,IntegrationConflictStatus status,OffsetDateTime detectedAt,
 OffsetDateTime resolvedAt,String resolvedBy,String resolutionAction,String resolutionReason,String correlationId) {}
