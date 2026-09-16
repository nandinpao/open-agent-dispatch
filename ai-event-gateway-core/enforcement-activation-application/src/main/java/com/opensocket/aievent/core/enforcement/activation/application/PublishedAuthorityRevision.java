package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;

public record PublishedAuthorityRevision(long revision, String checksum, Instant publishedAt) {
    public PublishedAuthorityRevision {
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        if (checksum == null || !checksum.startsWith("sha256:")) throw new IllegalArgumentException("checksum is required");
        if (publishedAt == null) throw new IllegalArgumentException("publishedAt is required");
    }
}
