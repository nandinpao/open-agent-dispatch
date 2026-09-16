package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
/** Metadata only. The secret value is never persisted or returned by this model. */
public record IntegrationCredentialMetadata(String tenantId,String credentialId,String principalId,IntegrationAuthType authType,String secretRef,String secretVersion,String secretLast4,OffsetDateTime validFrom,OffsetDateTime expiresAt,OffsetDateTime rotatedAt,OffsetDateTime lastUsedAt,IntegrationCredentialStatus status,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
