package com.opensocket.aievent.core.issuetracking.attachment;
import java.util.List;
import java.util.Optional;
/** Canonical Issue attachment metadata authority. Content bytes remain in an internal object store. */
public interface IssueAttachmentMetadataRepository {
 Optional<IssueAttachmentMetadata> find(String tenantId,String attachmentId);
 List<IssueAttachmentMetadata> listByLink(String tenantId,String taskIssueLinkId,int limit);
 IssueAttachmentMetadata save(IssueAttachmentMetadata metadata,long expectedVersion);
}
