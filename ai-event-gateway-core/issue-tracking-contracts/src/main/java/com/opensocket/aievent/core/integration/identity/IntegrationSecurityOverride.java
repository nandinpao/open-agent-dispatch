package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
public record IntegrationSecurityOverride(String tenantId,String overrideId,String principalId,String mappingId,String overrideType,String reason,String approvedBy,OffsetDateTime approvedAt,OffsetDateTime expiresAt,String status,String revokedBy,OffsetDateTime revokedAt,String correlationId,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){ public boolean activeAt(OffsetDateTime now){return "APPROVED".equals(status)&&expiresAt!=null&&expiresAt.isAfter(now);} }
