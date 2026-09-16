package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.integration.handoff.HandoffContextRepository;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityRepository;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Resolves Task/Issue attachment descriptors from canonical metadata and their trusted parent authority. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = {"enabled", "attachment-enabled"}, havingValue = "true")
public final class AttachmentResourceDescriptorResolver
        implements ResourceDescriptorResolverPort, ResourceParticipantResolverPort {
    private final List<ResourceAttachmentAuthorityPort> attachments;
    private final HandoffContextRepository handoffs;
    private final TaskIssueLinkRepository links;
    private final IntegrationIdentityRepository identities;
    private final TaskRepository tasks;

    public AttachmentResourceDescriptorResolver(
            List<ResourceAttachmentAuthorityPort> attachments,
            HandoffContextRepository handoffs,
            TaskIssueLinkRepository links,
            IntegrationIdentityRepository identities,
            TaskRepository tasks) {
        this.attachments = List.copyOf(attachments);
        this.handoffs = java.util.Objects.requireNonNull(handoffs);
        this.links = java.util.Objects.requireNonNull(links);
        this.identities = java.util.Objects.requireNonNull(identities);
        this.tasks = java.util.Objects.requireNonNull(tasks);
    }

    @Override
    public boolean supports(ResourceType type) {
        return type == ResourceType.TASK_ATTACHMENT || type == ResourceType.ISSUE_ATTACHMENT;
    }

    @Override
    public boolean supportsParticipants(ResourceType type) {
        return supports(type);
    }

    @Override
    public Optional<ResourceDescriptor> resolve(ResourceRef ref, DescriptorResolutionContext context) {
        if (!supports(ref.resourceType())) return Optional.empty();
        return attachment(ref).flatMap(metadata -> descriptor(metadata, context.requestedAt()));
    }

    @Override
    public Optional<ParticipantProjectionSnapshot> resolveParticipants(
            ResourceRef ref, DescriptorResolutionContext context) {
        Optional<ResourceDescriptor> resolved = resolve(ref, context);
        if (resolved.isEmpty()) return Optional.empty();
        ResourceDescriptor descriptor = resolved.get();
        List<ResourceParticipantProjection> values = new ArrayList<>();
        OwnershipDescriptor ownership = descriptor.ownership();
        add(
                values,
                ref,
                ResourceParticipantType.DEPARTMENT,
                ownership.ownerDepartmentId(),
                ResourceParticipantRole.OWNER,
                descriptor.visibility().maximumVisibility(),
                descriptor.participantVersion(),
                context.requestedAt(),
                descriptor.descriptorAuthority());
        add(
                values,
                ref,
                ResourceParticipantType.GROUP,
                ownership.ownerGroupId(),
                ResourceParticipantRole.OWNER,
                descriptor.visibility().maximumVisibility(),
                descriptor.participantVersion(),
                context.requestedAt(),
                descriptor.descriptorAuthority());
        return Optional.of(new ParticipantProjectionSnapshot(
                ref,
                descriptor.participantVersion(),
                values,
                descriptor.descriptorAuthority(),
                descriptor.descriptorHash(),
                context.requestedAt()));
    }

    private Optional<ResourceAttachmentMetadata> attachment(ResourceRef ref) {
        List<ResourceAttachmentMetadata> matches = attachments.stream()
                .map(authority -> authority.find(ref))
                .flatMap(Optional::stream)
                .toList();
        if (matches.size() > 1) throw new IllegalStateException("ATTACHMENT_AUTHORITY_AMBIGUOUS");
        return matches.stream().findFirst();
    }

    private Optional<ResourceDescriptor> descriptor(ResourceAttachmentMetadata metadata, Instant at) {
        return switch (metadata.attachmentRef().resourceType()) {
            case TASK_ATTACHMENT -> taskAttachment(metadata, at);
            case ISSUE_ATTACHMENT -> issueAttachment(metadata, at);
            default -> Optional.empty();
        };
    }

    private Optional<ResourceDescriptor> taskAttachment(ResourceAttachmentMetadata metadata, Instant at) {
        return handoffs.findSnapshot(metadata.attachmentRef().tenantId(), metadata.parentResourceRef().resourceId())
                .flatMap(snapshot -> tasks.findByTenantAndId(
                                metadata.attachmentRef().tenantId(), snapshot.sourceTaskId())
                        .map(task -> fromTask(metadata, task, DescriptorAuthority.TASK_DOMAIN, at)));
    }

    private Optional<ResourceDescriptor> issueAttachment(ResourceAttachmentMetadata metadata, Instant at) {
        if (metadata.parentResourceRef().resourceType() == ResourceType.TASK_ISSUE_LINK) {
            return links.findByTenantAndLinkId(
                            metadata.attachmentRef().tenantId(), metadata.parentResourceRef().resourceId())
                    .flatMap(link -> tasks.findByTenantAndId(
                                    metadata.attachmentRef().tenantId(), link.getTaskId())
                            .map(task -> fromTask(metadata, task, DescriptorAuthority.ISSUE_TRACKING, at)));
        }
        if (metadata.parentResourceRef().resourceType() == ResourceType.ISSUE_PROJECT_MAPPING) {
            return identities.findMapping(
                            metadata.attachmentRef().tenantId(), metadata.parentResourceRef().resourceId())
                    .map(mapping -> {
                        OwnershipDescriptor ownership = new OwnershipDescriptor(
                                BridgeDescriptorSupport.owner(mapping.departmentId()),
                                BridgeDescriptorSupport.owner(mapping.groupId()),
                                "",
                                "",
                                "",
                                BridgeDescriptorSupport.owner(mapping.departmentId()),
                                metadata.metadataVersion());
                        return descriptor(
                                metadata,
                                ownership,
                                metadata.parentResourceRef(),
                                metadata.parentResourceRef(),
                                DescriptorAuthority.ISSUE_TRACKING,
                                at);
                    });
        }
        return Optional.empty();
    }

    private ResourceDescriptor fromTask(
            ResourceAttachmentMetadata metadata,
            TaskRecord task,
            DescriptorAuthority authority,
            Instant at) {
        OwnershipDescriptor ownership = new OwnershipDescriptor(
                BridgeDescriptorSupport.owner(task.getOwnerDepartmentId()),
                BridgeDescriptorSupport.owner(task.getOwnerGroupId()),
                "",
                "",
                BridgeDescriptorSupport.owner(task.getRequesterDepartmentId()),
                BridgeDescriptorSupport.owner(task.getExecutorDepartmentId()),
                Math.max(metadata.metadataVersion(), task.getVersion()));
        ResourceRef root = new ResourceRef(
                metadata.attachmentRef().tenantId(),
                ResourceType.TASK,
                task.getRootTaskId() == null || task.getRootTaskId().isBlank()
                        ? task.getTaskId()
                        : task.getRootTaskId());
        return descriptor(metadata, ownership, metadata.parentResourceRef(), root, authority, at);
    }

    private ResourceDescriptor descriptor(
            ResourceAttachmentMetadata metadata,
            OwnershipDescriptor ownership,
            ResourceRef parent,
            ResourceRef root,
            DescriptorAuthority authority,
            Instant at) {
        ResourceSecurityState state = switch (metadata.malwareStatus()) {
            case INFECTED, SCAN_FAILED -> ResourceSecurityState.QUARANTINED;
            case NOT_SCANNED, PENDING -> ResourceSecurityState.RESTRICTED;
            case CLEAN -> metadata.legalHold() ? ResourceSecurityState.LEGAL_HOLD : ResourceSecurityState.NORMAL;
        };
        VisibilityDescriptor visibility = BridgeDescriptorSupport.visibility(
                SensitivityLevel.RESTRICTED,
                VisibilityLevel.SENSITIVE,
                "ATTACHMENT_CONTENT",
                metadata.metadataVersion());
        long participantVersion = BridgeDescriptorSupport.revisionToken(String.join(
                "|",
                ownership.ownerDepartmentId(),
                ownership.ownerGroupId(),
                String.valueOf(ownership.ownershipVersion()),
                String.valueOf(metadata.metadataVersion())));
        long resourceVersion = Math.max(metadata.metadataVersion(), ownership.ownershipVersion());
        String canonical = String.join(
                "|",
                metadata.filename(),
                metadata.contentType(),
                String.valueOf(metadata.sizeBytes()),
                metadata.sha256(),
                metadata.malwareStatus().name(),
                String.valueOf(metadata.contentAvailable()),
                String.valueOf(metadata.legalHold()),
                String.valueOf(metadata.metadataVersion()),
                ownership.ownerDepartmentId(),
                ownership.ownerGroupId(),
                ownership.requesterDepartmentId(),
                ownership.executorDepartmentId(),
                parent == null ? "" : parent.resourceType() + ":" + parent.resourceId(),
                root == null ? "" : root.resourceType() + ":" + root.resourceId());
        return new ResourceDescriptor(
                metadata.attachmentRef(),
                metadata.filename(),
                ownership,
                parent,
                root,
                visibility,
                state,
                participantVersion,
                resourceVersion,
                authority,
                BridgeDescriptorSupport.hash(metadata.attachmentRef(), canonical),
                at);
    }

    private void add(
            List<ResourceParticipantProjection> output,
            ResourceRef ref,
            ResourceParticipantType type,
            String id,
            ResourceParticipantRole role,
            VisibilityLevel visibility,
            long version,
            Instant at,
            DescriptorAuthority authority) {
        if (id == null || id.isBlank()) return;
        output.add(new ResourceParticipantProjection(
                BridgeDescriptorSupport.participantId(ref, type, id, role),
                ref,
                type,
                id,
                role,
                visibility,
                List.of(ref.resourceType() == ResourceType.TASK_ATTACHMENT
                        ? "task.attachment.read"
                        : "integration.issue.attachment.read"),
                at,
                null,
                authority,
                version,
                ResourceParticipantStatus.ACTIVE));
    }
}
