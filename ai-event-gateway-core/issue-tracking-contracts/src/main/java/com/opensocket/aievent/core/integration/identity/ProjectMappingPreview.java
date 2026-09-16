package com.opensocket.aievent.core.integration.identity;
import java.util.*;
public record ProjectMappingPreview(String mappingId,int mappingVersion,String providerProject,String issueType,String renderedSummary,String renderedDescription,Map<String,Object> mappedFields,List<String> missingRequiredFields,String metadataSnapshotId,String metadataSchemaHash) {
 public ProjectMappingPreview { mappedFields=mappedFields==null?Map.of():Map.copyOf(mappedFields); missingRequiredFields=missingRequiredFields==null?List.of():List.copyOf(missingRequiredFields); }
}
