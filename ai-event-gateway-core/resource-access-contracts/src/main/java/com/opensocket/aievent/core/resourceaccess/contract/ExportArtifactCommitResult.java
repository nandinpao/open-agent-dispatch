package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Commit evidence only; artifact storage remains outside Resource Access. */
public record ExportArtifactCommitResult(String exportAuthorizationId,String runtimeLeaseId,long rowCount,String fieldSetHash,String artifactSha256,long artifactSizeBytes,Instant committedAt,boolean accepted,String reasonCode){
 public ExportArtifactCommitResult{if(exportAuthorizationId==null||exportAuthorizationId.isBlank()||runtimeLeaseId==null||runtimeLeaseId.isBlank()||committedAt==null)throw new IllegalArgumentException("commit fields are required");reasonCode=reasonCode==null?"":reasonCode.trim();}
}
