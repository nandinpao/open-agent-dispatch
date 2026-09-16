package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;

/** Opaque short-lived content handle. Internal storage references never cross this contract. */
public record AttachmentContentHandle(String handleId, Instant expiresAt) {
    public AttachmentContentHandle {
        if (handleId == null || handleId.isBlank()) throw new IllegalArgumentException("handleId is required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
        handleId = handleId.trim();
    }
}
