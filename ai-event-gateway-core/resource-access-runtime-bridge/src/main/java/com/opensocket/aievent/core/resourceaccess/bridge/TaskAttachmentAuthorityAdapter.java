package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.integration.handoff.HandoffAttachmentMetadata;
import com.opensocket.aievent.core.integration.handoff.HandoffContextRepository;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Canonical Task attachment authority: immutable Handoff metadata plus independently verified security/storage evidence. */
@Component
@ConditionalOnProperty(prefix="resource-access",name={"enabled","attachment-enabled"},havingValue="true")
public final class TaskAttachmentAuthorityAdapter implements ResourceAttachmentAuthorityPort {
    private final HandoffContextRepository handoffs;
    private final AttachmentSecurityMetadataPort security;
    public TaskAttachmentAuthorityAdapter(HandoffContextRepository handoffs,AttachmentSecurityMetadataPort security){this.handoffs=Objects.requireNonNull(handoffs);this.security=Objects.requireNonNull(security);}
    @Override public Optional<ResourceAttachmentMetadata> find(ResourceRef ref){
        if(ref.resourceType()!=ResourceType.TASK_ATTACHMENT)return Optional.empty();
        AttachmentResourceIdentity.TaskAttachmentParts parts;
        try{parts=AttachmentResourceIdentity.parseTaskAttachmentId(ref.resourceId());}catch(IllegalArgumentException e){return Optional.empty();}
        return handoffs.findSnapshot(ref.tenantId(),parts.snapshotId()).flatMap(snapshot->snapshot.attachmentMetadata().stream().filter(a->parts.sha256().equalsIgnoreCase(text(a.sha256()))).findFirst().map(a->metadata(ref,parts.snapshotId(),a,snapshot.rowVersion(),snapshot.createdAt()==null?Instant.EPOCH:snapshot.createdAt().toInstant())));
    }
    private ResourceAttachmentMetadata metadata(ResourceRef ref,String snapshotId,HandoffAttachmentMetadata value,long snapshotVersion,Instant observedAt){
        AttachmentSecurityMetadata secured=security.find(ref).orElse(new AttachmentSecurityMetadata(ref,"",AttachmentMalwareStatus.NOT_SCANNED,false,false,1,observedAt));
        return new ResourceAttachmentMetadata(ref,new ResourceRef(ref.tenantId(),ResourceType.TASK_CONTEXT_SNAPSHOT,snapshotId),required(value.filename(),"filename"),required(value.contentType(),"contentType"),Math.max(0,value.sizeBytes()),required(value.sha256(),"sha256"),secured.storageObjectRef(),"",secured.malwareStatus(),secured.contentAvailable(),secured.legalHold(),Math.max(snapshotVersion,secured.version()),observedAt);
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalStateException("Task attachment "+f+" is missing");return v.trim();}
    private static String text(String v){return v==null?"":v.trim();}
}
