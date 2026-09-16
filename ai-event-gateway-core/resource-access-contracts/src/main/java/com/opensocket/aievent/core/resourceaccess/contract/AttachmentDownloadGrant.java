package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Executable, short-lived download evidence. No URL, secret or Provider credential is exposed. */
public record AttachmentDownloadGrant(ResourceRef attachmentRef,String filename,String contentType,long sizeBytes,String sha256,String readDecisionId,String downloadDecisionId,String runtimeLeaseId,long fencingVersion,AttachmentContentHandle contentHandle,Instant issuedAt){
 public AttachmentDownloadGrant{if(attachmentRef==null||contentHandle==null||issuedAt==null)throw new IllegalArgumentException("attachment grant fields are required");filename=required(filename,"filename");contentType=required(contentType,"contentType");sha256=required(sha256,"sha256");readDecisionId=required(readDecisionId,"readDecisionId");downloadDecisionId=required(downloadDecisionId,"downloadDecisionId");runtimeLeaseId=required(runtimeLeaseId,"runtimeLeaseId");if(sizeBytes<0||fencingVersion<0)throw new IllegalArgumentException("sizeBytes and fencingVersion must be non-negative");}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
