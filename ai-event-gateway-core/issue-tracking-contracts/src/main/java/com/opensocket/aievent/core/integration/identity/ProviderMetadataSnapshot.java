package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.*;
public record ProviderMetadataSnapshot(
 String tenantId,String snapshotId,String connectionId,String mappingId,String providerProjectId,String providerProjectKey,
 List<ProviderProjectMetadata> projects,List<ProviderIssueTypeMetadata> issueTypes,List<ProviderFieldMetadata> fields,
 List<ProviderTransitionMetadata> transitions,List<ProviderLinkTypeMetadata> linkTypes,List<ProviderIdentityMetadata> users,
 List<ProviderIdentityMetadata> groups,Map<String,PermissionProbeResultStatus> permissions,String etag,String lastModified,
 String schemaHash,int metadataVersion,ProviderMetadataCacheStatus cacheStatus,OffsetDateTime probedAt,OffsetDateTime expiresAt,
 String providerSummary,String correlationId) {
 public ProviderMetadataSnapshot {
  projects=projects==null?List.of():List.copyOf(projects); issueTypes=issueTypes==null?List.of():List.copyOf(issueTypes);
  fields=fields==null?List.of():List.copyOf(fields); transitions=transitions==null?List.of():List.copyOf(transitions);
  linkTypes=linkTypes==null?List.of():List.copyOf(linkTypes); users=users==null?List.of():List.copyOf(users);
  groups=groups==null?List.of():List.copyOf(groups); permissions=permissions==null?Map.of():Map.copyOf(permissions);
 }
 public boolean expired(OffsetDateTime now){return expiresAt!=null&&!expiresAt.isAfter(now);}
}
