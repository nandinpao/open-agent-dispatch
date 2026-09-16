package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
public record IntegrationProjectMappingVersion(String tenantId,String mappingId,int mappingVersion,ProjectMappingLifecycle lifecycle,String configurationJson,String configurationHash,String metadataSnapshotId,String metadataSchemaHash,String createdBy,OffsetDateTime createdAt) {}
