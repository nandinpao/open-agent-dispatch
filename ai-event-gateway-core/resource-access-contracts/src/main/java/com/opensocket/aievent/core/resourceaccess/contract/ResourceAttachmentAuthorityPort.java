package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Optional;
/** Canonical metadata authority for Task and Issue attachments. Content retrieval is deliberately separate. */
public interface ResourceAttachmentAuthorityPort {
    Optional<ResourceAttachmentMetadata> find(ResourceRef attachmentRef);
}
