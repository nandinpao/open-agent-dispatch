package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;
public record IntegrationCircuitBreaker(String tenantId,String connectionId,String projectMappingId,IntegrationCircuitState state,
 int consecutiveFailures,int failureThreshold,OffsetDateTime openedAt,OffsetDateTime nextProbeAt,OffsetDateTime lastSuccessAt,
 OffsetDateTime lastFailureAt,String lastErrorCode,OffsetDateTime updatedAt) {}
