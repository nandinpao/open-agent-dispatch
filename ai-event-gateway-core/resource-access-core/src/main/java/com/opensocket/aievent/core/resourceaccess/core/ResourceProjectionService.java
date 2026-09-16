package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Creates an atomic, one-way Resource Access projection from canonical Domain authorities. */
public final class ResourceProjectionService {
    private final AuthoritativeResourceDescriptorService descriptors;
    private final AuthoritativeResourceParticipantService participants;
    private final ResourceProjectionRepository repository;
    private final OwnershipRequirementPolicy ownershipPolicy;
    public ResourceProjectionService(AuthoritativeResourceDescriptorService descriptors,
            AuthoritativeResourceParticipantService participants, ResourceProjectionRepository repository,
            OwnershipRequirementPolicy ownershipPolicy) {
        this.descriptors = Objects.requireNonNull(descriptors); this.participants = Objects.requireNonNull(participants);
        this.repository = Objects.requireNonNull(repository); this.ownershipPolicy = Objects.requireNonNull(ownershipPolicy);
    }
    public ResourceProjectionSyncResult project(ResourceRef ref, DescriptorResolutionContext context, String sourceEventId) {
        ResourceDescriptor resolved = descriptors.resolve(ref, context);
        ParticipantProjectionSnapshot snapshot = participants.resolve(ref, context, resolved.descriptorAuthority());
        if (snapshot.participantVersion() != resolved.participantVersion())
            throw new IllegalStateException("RESOURCE_PARTICIPANT_VERSION_MISMATCH");
        ResourceDescriptor normalized = normalizeOrphanState(resolved);
        Instant now = context.requestedAt();
        String eventType = repository.findDescriptor(ref).isEmpty() ? "RESOURCE_DESCRIPTOR_CREATED" : "RESOURCE_DESCRIPTOR_SYNCHRONIZED";
        String eventId = "rap-" + UUID.nameUUIDFromBytes((ref.tenantId()+":"+ref.resourceType()+":"+ref.resourceId()+":"+normalized.descriptorHash()+":"+snapshot.participantVersion()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResourceProjectionEvent event = new ResourceProjectionEvent(eventId, ref, eventType, sourceEventId,
                DescriptorFingerprint.sha256(normalized.descriptorHash()+":"+snapshot.participantVersion()), now);
        return repository.synchronize(new ResourceProjectionBatch(normalized, snapshot, event, sourceEventId, now));
    }
    private ResourceDescriptor normalizeOrphanState(ResourceDescriptor descriptor) {
        boolean orphan = ownershipPolicy.requiresOwner(descriptor.resourceRef().resourceType()) && !descriptor.ownership().hasOwner();
        if (!orphan || descriptor.securityState() == ResourceSecurityState.ORPHANED) return descriptor;
        return new ResourceDescriptor(descriptor.resourceRef(), descriptor.resourceKey(), descriptor.ownership(),
                descriptor.parentResource(), descriptor.rootResource(), descriptor.visibility(), ResourceSecurityState.ORPHANED,
                descriptor.participantVersion(), descriptor.resourceVersion(), descriptor.descriptorAuthority(),
                DescriptorFingerprint.sha256(descriptor.descriptorHash()+":ORPHANED"), descriptor.resolvedAt());
    }
}
