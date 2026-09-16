package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Optional;
/** Security scanner/storage projection authority. */
public interface AttachmentSecurityMetadataPort {
 Optional<AttachmentSecurityMetadata> find(ResourceRef attachmentRef);
 AttachmentSecurityMetadata save(AttachmentSecurityMetadata metadata,long expectedVersion,String actorId,String reason,String correlationId);
}
