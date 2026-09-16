package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
public record IntegrationProviderAuthorizationFailure(String tenantId,String failureId,String connectionId,String principalId,String credentialId,String mappingId,int providerStatus,IntegrationOperation operation,String reasonCode,String reprobeStatus,String correlationId,OffsetDateTime occurredAt,OffsetDateTime reprobedAt) {}
