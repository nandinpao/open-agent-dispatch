package com.opensocket.aievent.core.issuetracking.recovery;
public record ProviderReadbackCommand(String tenantId,String connectionId,String projectMappingId,String projectionId,String externalIdempotencyMarker,String expectedHash,String externalIssueId,String correlationId){}
