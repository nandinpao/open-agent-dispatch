package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.UUID;

public record Wave0PermissionCatalogView(
        UUID revisionId,
        String revisionCode,
        long revisionNumber,
        String status,
        String contentHash,
        int entryCount,
        int aliasCount,
        Instant publishedAt) implements Wave0CanonicalPayload {

    public Wave0PermissionCatalogView {
        if (revisionId == null) throw new IllegalArgumentException("revisionId is required");
        revisionCode = normalize(revisionCode);
        status = normalize(status);
        contentHash = normalize(contentHash);
        if (revisionNumber < 0 || entryCount < 0 || aliasCount < 0) throw new IllegalArgumentException("catalog counts are invalid");
    }

    @Override public String canonicalValue() {
        return revisionId + "|" + revisionCode + "|" + revisionNumber + "|" + status + "|" + contentHash + "|" + entryCount + "|" + aliasCount + "|" + publishedAt;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
