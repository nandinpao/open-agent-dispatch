package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IssueRelationship(String tenantId,String relationshipId,String fromTaskIssueLinkId,String toTaskIssueLinkId,
 IssueRelationshipType relationshipType,String providerRelationId,IntegrationSyncStatus syncStatus,String payloadHash,
 String idempotencyKey,String lastErrorCode,String lastErrorMessage,long resourceVersion,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
