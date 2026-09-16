package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record ExternalIssueObservation(
 String tenantId,String observationId,String inboxId,String connectionId,String providerType,String providerEventId,
 Long providerEventSequence,String providerChangeVersion,String externalProjectId,String externalIssueId,String externalIssueKey,
 String externalIssueStatus,String rawObservedDocumentJson,String normalizedObservationJson,String observedDocumentHash,
 String normalizationProfileVersion,String providerIdentityHash,String mappingSchemaHash,OffsetDateTime providerObservedAt,
 OffsetDateTime createdAt,String correlationId) {}
