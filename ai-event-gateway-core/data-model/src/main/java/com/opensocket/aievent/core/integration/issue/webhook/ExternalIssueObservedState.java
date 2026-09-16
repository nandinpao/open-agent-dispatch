package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record ExternalIssueObservedState(
 String tenantId,String stateId,String connectionId,String externalProjectId,String externalIssueId,String externalIssueKey,
 String taskIssueLinkId,String projectionId,String latestObservationId,String lastAppliedProviderEventId,
 Long providerEventSequence,String providerChangeVersion,OffsetDateTime lastAppliedEventAt,String desiredDocumentHash,
 String observedDocumentHash,String normalizedObservationJson,String diffJson,String diffHash,String providerIdentityHash,
 String mappingSchemaHash,ExternalIssueConflictClassification conflictClassification,OffsetDateTime observedAt,long version,
 OffsetDateTime updatedAt) {}
