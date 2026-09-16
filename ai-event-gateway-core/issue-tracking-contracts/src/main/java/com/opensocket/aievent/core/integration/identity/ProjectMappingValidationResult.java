package com.opensocket.aievent.core.integration.identity;
import java.util.*;
public record ProjectMappingValidationResult(String mappingId,int mappingVersion,boolean valid,List<String> errors,List<String> warnings,String metadataSnapshotId,String metadataSchemaHash) {
 public ProjectMappingValidationResult { errors=errors==null?List.of():List.copyOf(errors); warnings=warnings==null?List.of():List.copyOf(warnings); }
}
