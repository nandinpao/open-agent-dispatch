package com.opensocket.aievent.database.persistence.integrationidentity.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor
public class ProviderMetadataSnapshotPo { private String tenantId,snapshotId,connectionId,mappingId,providerProjectId,providerProjectKey,payloadJson,etag,lastModified,schemaHash,cacheStatus,providerSummary,correlationId; private int metadataVersion; private OffsetDateTime probedAt,expiresAt; }
