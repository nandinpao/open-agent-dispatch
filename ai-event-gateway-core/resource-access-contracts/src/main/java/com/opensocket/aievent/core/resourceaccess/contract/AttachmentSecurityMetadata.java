package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Security-scan and internal-storage evidence for an attachment. It contains no Provider URL or credential. */
public record AttachmentSecurityMetadata(ResourceRef attachmentRef,String storageObjectRef,AttachmentMalwareStatus malwareStatus,boolean contentAvailable,boolean legalHold,long version,Instant scannedAt){
 public AttachmentSecurityMetadata{if(attachmentRef==null||malwareStatus==null||scannedAt==null)throw new IllegalArgumentException("attachment security fields are required");storageObjectRef=storageObjectRef==null?"":storageObjectRef.trim();String v=storageObjectRef.toLowerCase(java.util.Locale.ROOT);if(v.startsWith("http://")||v.startsWith("https://")||v.startsWith("ftp://"))throw new IllegalArgumentException("Provider direct URLs are forbidden");if(contentAvailable&&storageObjectRef.isBlank())throw new IllegalArgumentException("contentAvailable requires storageObjectRef");if(version<1)throw new IllegalArgumentException("version must be positive");}
}
