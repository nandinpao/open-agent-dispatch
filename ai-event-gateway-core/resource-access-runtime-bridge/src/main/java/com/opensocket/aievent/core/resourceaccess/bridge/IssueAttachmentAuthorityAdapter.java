package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadata;
import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadataRepository;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Provider-neutral Issue attachment metadata authority. Direct Provider URLs are never accepted or returned. */
@Component
@ConditionalOnProperty(prefix="resource-access",name={"enabled","attachment-enabled"},havingValue="true")
public final class IssueAttachmentAuthorityAdapter implements ResourceAttachmentAuthorityPort {
    private final IssueAttachmentMetadataRepository repository;
    public IssueAttachmentAuthorityAdapter(IssueAttachmentMetadataRepository repository){this.repository=Objects.requireNonNull(repository);}
    @Override public Optional<ResourceAttachmentMetadata> find(ResourceRef ref){
        if(ref.resourceType()!=ResourceType.ISSUE_ATTACHMENT)return Optional.empty();
        return repository.find(ref.tenantId(),ref.resourceId()).map(v->convert(ref,v));
    }
    private ResourceAttachmentMetadata convert(ResourceRef ref,IssueAttachmentMetadata v){
        ResourceRef parent=!v.taskIssueLinkId().isBlank()?new ResourceRef(ref.tenantId(),ResourceType.TASK_ISSUE_LINK,v.taskIssueLinkId()):new ResourceRef(ref.tenantId(),ResourceType.ISSUE_PROJECT_MAPPING,v.projectMappingId());
        AttachmentMalwareStatus status;
        try{status=AttachmentMalwareStatus.valueOf(v.malwareStatus().trim().toUpperCase(java.util.Locale.ROOT));}catch(RuntimeException e){status=AttachmentMalwareStatus.SCAN_FAILED;}
        return new ResourceAttachmentMetadata(ref,parent,v.filename(),v.contentType(),v.sizeBytes(),v.sha256(),v.storageObjectRef(),v.providerAttachmentId(),status,v.contentAvailable(),v.legalHold(),v.metadataVersion(),v.observedAt().toInstant());
    }
}
