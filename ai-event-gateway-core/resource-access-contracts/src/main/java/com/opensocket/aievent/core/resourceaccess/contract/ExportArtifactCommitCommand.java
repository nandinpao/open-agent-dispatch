package com.opensocket.aievent.core.resourceaccess.contract;
/** Final checkpoint before publishing an export artifact. */
public record ExportArtifactCommitCommand(String tenantId,String exportAuthorizationId,String runtimeLeaseId,long presentedFencingVersion,long rowCount,String fieldSetHash,String artifactSha256,long artifactSizeBytes,String correlationId){
 public ExportArtifactCommitCommand{tenantId=required(tenantId,"tenantId");exportAuthorizationId=required(exportAuthorizationId,"exportAuthorizationId");runtimeLeaseId=required(runtimeLeaseId,"runtimeLeaseId");if(presentedFencingVersion<0||rowCount<0||artifactSizeBytes<0)throw new IllegalArgumentException("versions and counts must be non-negative");fieldSetHash=required(fieldSetHash,"fieldSetHash");artifactSha256=required(artifactSha256,"artifactSha256").toLowerCase(java.util.Locale.ROOT);if(!artifactSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("artifactSha256 must be lowercase SHA-256");correlationId=required(correlationId,"correlationId");}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
