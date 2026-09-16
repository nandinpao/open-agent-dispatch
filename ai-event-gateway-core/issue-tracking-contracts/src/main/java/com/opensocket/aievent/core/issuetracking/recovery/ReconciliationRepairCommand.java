package com.opensocket.aievent.core.issuetracking.recovery;
public record ReconciliationRepairCommand(String tenantId,String connectionId,String projectMappingId,ProjectionRecoveryCaseType caseType,String projectionId,String externalIssueId,String expectedHash,boolean dryRun,String idempotencyKey,String correlationId){}
