package com.opensocket.aievent.database.persistence.integrationidentity.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor
public class IntegrationProjectMappingVersionPo { private String tenantId,mappingId,lifecycle,configurationJson,configurationHash,metadataSnapshotId,metadataSchemaHash,createdBy; private int mappingVersion; private OffsetDateTime createdAt; }
