package com.opensocket.aievent.core.integration.handoff;

/** Provider-neutral attachment evidence. sourceReference may identify any source artifact. */
public record HandoffAttachmentMetadata(
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        String sourceReference,
        AttachmentAvailabilityStatus availabilityStatus) {
}
