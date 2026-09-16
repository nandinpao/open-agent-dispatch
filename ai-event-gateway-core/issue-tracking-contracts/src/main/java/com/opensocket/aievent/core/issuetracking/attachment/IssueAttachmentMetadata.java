package com.opensocket.aievent.core.issuetracking.attachment;

import java.time.OffsetDateTime;

/** Provider-neutral Issue attachment metadata. Provider direct URLs and credentials are never persisted. */
public record IssueAttachmentMetadata(
        String tenantId,
        String attachmentId,
        String connectionId,
        String taskIssueLinkId,
        String projectMappingId,
        String providerAttachmentId,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageObjectRef,
        String malwareStatus,
        boolean contentAvailable,
        boolean legalHold,
        long metadataVersion,
        OffsetDateTime observedAt) {
    public IssueAttachmentMetadata {
        tenantId=required(tenantId,"tenantId");attachmentId=required(attachmentId,"attachmentId");connectionId=required(connectionId,"connectionId");
        taskIssueLinkId=normalize(taskIssueLinkId);projectMappingId=normalize(projectMappingId);providerAttachmentId=required(providerAttachmentId,"providerAttachmentId");
        if(taskIssueLinkId.isBlank()==projectMappingId.isBlank())throw new IllegalArgumentException("Issue attachment requires exactly one Task-Issue Link or Project Mapping authority");
        filename=required(filename,"filename");contentType=required(contentType,"contentType");if(sizeBytes<0)throw new IllegalArgumentException("sizeBytes must be non-negative");
        sha256=required(sha256,"sha256").toLowerCase(java.util.Locale.ROOT);if(!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("sha256 must be lowercase SHA-256");
        storageObjectRef=normalize(storageObjectRef);if(url(storageObjectRef))throw new IllegalArgumentException("Provider direct URLs are forbidden");
        malwareStatus=required(malwareStatus,"malwareStatus");if(contentAvailable&&storageObjectRef.isBlank())throw new IllegalArgumentException("contentAvailable requires storageObjectRef");
        if(metadataVersion<1)throw new IllegalArgumentException("metadataVersion must be positive");if(observedAt==null)throw new IllegalArgumentException("observedAt is required");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
    private static String normalize(String v){return v==null?"":v.trim();}
    private static boolean url(String v){String n=normalize(v).toLowerCase(java.util.Locale.ROOT);return n.startsWith("http://")||n.startsWith("https://")||n.startsWith("ftp://");}
}
