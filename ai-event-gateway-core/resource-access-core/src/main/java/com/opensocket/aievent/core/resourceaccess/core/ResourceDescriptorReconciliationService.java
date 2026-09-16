package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.DescriptorResolutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Detects missing/stale descriptor projections and repairs projections only; it never mutates Domain ownership. */
public final class ResourceDescriptorReconciliationService {
    private final AuthoritativeResourceDescriptorService authority;
    private final ResourceProjectionRepository repository;
    private final ResourceProjectionService projection;
    private final OwnershipRequirementPolicy ownershipPolicy;
    public ResourceDescriptorReconciliationService(AuthoritativeResourceDescriptorService authority,
            ResourceProjectionRepository repository, ResourceProjectionService projection, OwnershipRequirementPolicy ownershipPolicy) {
        this.authority=Objects.requireNonNull(authority);this.repository=Objects.requireNonNull(repository);this.projection=Objects.requireNonNull(projection);this.ownershipPolicy=Objects.requireNonNull(ownershipPolicy);
    }
    public ResourceReconciliationRecord reconcile(ResourceRef ref, DescriptorResolutionContext context, boolean autoRepair) {
        Instant at=context.requestedAt(); ResourceDescriptor source=normalize(authority.resolve(ref,context));
        var projected=repository.findDescriptor(ref);
        ResourceReconciliationStatus status; String reason; boolean repaired=false;
        if(projected.isEmpty()){status=ResourceReconciliationStatus.MISSING_PROJECTION;reason="RESOURCE_DESCRIPTOR_MISSING";}
        else if(!projected.get().descriptorAuthority().equals(source.descriptorAuthority())){status=ResourceReconciliationStatus.AUTHORITY_MISMATCH;reason="RESOURCE_DESCRIPTOR_AUTHORITY_MISMATCH";}
        else if(!projected.get().descriptorHash().equals(source.descriptorHash()) || projected.get().resourceVersion()<source.resourceVersion()){status=ResourceReconciliationStatus.STALE_PROJECTION;reason="RESOURCE_DESCRIPTOR_STALE";}
        else if(projected.get().securityState()==com.opensocket.aievent.core.resourceaccess.contract.ResourceSecurityState.ORPHANED){status=ResourceReconciliationStatus.ORPHANED;reason="RESOURCE_OWNER_MISSING";}
        else{status=ResourceReconciliationStatus.MATCHED;reason="RESOURCE_DESCRIPTOR_MATCHED";}
        if(autoRepair && (status==ResourceReconciliationStatus.MISSING_PROJECTION || status==ResourceReconciliationStatus.STALE_PROJECTION)){
            projection.project(ref,context,"reconcile:"+context.correlationId()); repaired=true; status=ResourceReconciliationStatus.REPAIRED;
        }
        String id="rar-"+UUID.nameUUIDFromBytes((ref+":"+at+":"+reason).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResourceReconciliationRecord record=new ResourceReconciliationRecord(id,ref,status,projected.map(ResourceDescriptor::descriptorHash).orElse(""),source.descriptorHash(),reason,repaired,at);
        repository.recordReconciliation(record); return record;
    }
    private ResourceDescriptor normalize(ResourceDescriptor descriptor){
        boolean orphan=ownershipPolicy.requiresOwner(descriptor.resourceRef().resourceType())&&!descriptor.ownership().hasOwner();
        if(!orphan||descriptor.securityState()==com.opensocket.aievent.core.resourceaccess.contract.ResourceSecurityState.ORPHANED)return descriptor;
        return new ResourceDescriptor(descriptor.resourceRef(),descriptor.resourceKey(),descriptor.ownership(),descriptor.parentResource(),descriptor.rootResource(),descriptor.visibility(),com.opensocket.aievent.core.resourceaccess.contract.ResourceSecurityState.ORPHANED,descriptor.participantVersion(),descriptor.resourceVersion(),descriptor.descriptorAuthority(),DescriptorFingerprint.sha256(descriptor.descriptorHash()+":ORPHANED"),descriptor.resolvedAt());
    }
}
