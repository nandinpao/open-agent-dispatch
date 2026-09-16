package com.opensocket.aievent.core.resourceaccess.contract;
/** Canonical opaque-enough attachment identifiers. Task attachments bind immutable snapshot and SHA-256. */
public final class AttachmentResourceIdentity {
 private AttachmentResourceIdentity(){}
 public static String taskAttachmentId(String snapshotId,String sha256){String s=required(snapshotId,"snapshotId");String h=required(sha256,"sha256").toLowerCase(java.util.Locale.ROOT);if(!h.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("sha256 must be lowercase SHA-256");if(s.contains("~"))throw new IllegalArgumentException("snapshotId must not contain ~");return s+"~"+h;}
 public static TaskAttachmentParts parseTaskAttachmentId(String resourceId){String id=required(resourceId,"resourceId");int p=id.lastIndexOf('~');if(p<1||p==id.length()-1)throw new IllegalArgumentException("Invalid Task attachment resourceId");String snapshot=id.substring(0,p),hash=id.substring(p+1).toLowerCase(java.util.Locale.ROOT);if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid Task attachment SHA-256");return new TaskAttachmentParts(snapshot,hash);}
 public record TaskAttachmentParts(String snapshotId,String sha256){}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
