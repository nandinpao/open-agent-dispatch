package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.*;
public interface IntegrationWebhookEndpointRepository {
 IntegrationWebhookEndpoint save(IntegrationWebhookEndpoint value);
 Optional<IntegrationWebhookEndpoint> find(String tenantId,String endpointId);
 Optional<IntegrationWebhookEndpoint> findByTokenHash(String endpointTokenHash);
 List<IntegrationWebhookEndpoint> list(String tenantId,String connectionId,int limit);
 boolean reserveNonce(String tenantId,String connectionId,String endpointId,String nonceHash,OffsetDateTime expiresAt,String correlationId);
 int deleteExpiredNonces(OffsetDateTime before,int limit);
 String mode();
}
