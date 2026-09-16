package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
/** Opaque external Webhook route bound to one Tenant, Connection and machine Principal. The raw endpoint token is never persisted. */
public record IntegrationWebhookEndpoint(
 String tenantId,String endpointId,String endpointTokenHash,String connectionId,String principalId,
 IntegrationProviderType providerType,IntegrationWebhookEndpointStatus status,String signatureAlgorithm,
 int maxBodyBytes,int rateLimitPerMinute,long replayWindowSeconds,long version,
 OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
