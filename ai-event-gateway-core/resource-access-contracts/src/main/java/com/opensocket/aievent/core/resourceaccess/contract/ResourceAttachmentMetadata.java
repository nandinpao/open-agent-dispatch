package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Provider-neutral attachment authority metadata. It never contains a URL, credential or secret reference. */
public record ResourceAttachmentMetadata(
        ResourceRef attachmentRef,
        ResourceRef parentResourceRef,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageObjectRef,
        String sourceProviderObjectId,
        AttachmentMalwareStatus malwareStatus,
        boolean contentAvailable,
        boolean legalHold,
        long metadataVersion,
        Instant observedAt) {
    public ResourceAttachmentMetadata {
        Objects.requireNonNull(attachmentRef, "attachmentRef");
        Objects.requireNonNull(parentResourceRef, "parentResourceRef");
        filename = required(filename, "filename");
        contentType = required(contentType, "contentType").toLowerCase(java.util.Locale.ROOT);
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
        sha256 = required(sha256, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        storageObjectRef = normalize(storageObjectRef);
        sourceProviderObjectId = normalize(sourceProviderObjectId);
        if (looksLikeUrl(storageObjectRef) || looksLikeUrl(sourceProviderObjectId))
            throw new IllegalArgumentException("Provider direct URLs are forbidden in attachment authority metadata");
        Objects.requireNonNull(malwareStatus, "malwareStatus");
        if (contentAvailable && storageObjectRef.isBlank()) throw new IllegalArgumentException("contentAvailable requires an internal storageObjectRef");
        if (metadataVersion < 1) throw new IllegalArgumentException("metadataVersion must be positive");
        Objects.requireNonNull(observedAt, "observedAt");
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String normalize(String value){return value==null?"":value.trim();}
    private static boolean looksLikeUrl(String value){String v=normalize(value).toLowerCase(java.util.Locale.ROOT);return v.startsWith("http://")||v.startsWith("https://")||v.startsWith("ftp://");}
}
