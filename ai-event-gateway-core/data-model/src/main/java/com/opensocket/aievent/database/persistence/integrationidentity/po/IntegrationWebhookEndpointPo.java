package com.opensocket.aievent.database.persistence.integrationidentity.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor
public class IntegrationWebhookEndpointPo {
 private String tenantId,endpointId,endpointTokenHash,connectionId,principalId,providerType,status,signatureAlgorithm;
 private int maxBodyBytes,rateLimitPerMinute; private long replayWindowSeconds,version; private OffsetDateTime createdAt,updatedAt;
}
