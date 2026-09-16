package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Resource Access projection persistence port. Implementations are not Domain ownership authorities. */
public interface ResourceProjectionRepository {
    Optional<ResourceDescriptor> findDescriptor(ResourceRef resourceRef);
    ResourceProjectionSyncResult synchronize(ResourceProjectionBatch batch);
    List<ResourceRef> findStaleDescriptors(Instant projectedBefore, int limit);
    void recordReconciliation(ResourceReconciliationRecord record);
    java.util.Optional<com.opensocket.aievent.core.resourceaccess.contract.OwnershipTransferResult> findOwnershipTransfer(
            ResourceRef resourceRef, String idempotencyKey);
    void recordOwnershipTransfer(com.opensocket.aievent.core.resourceaccess.contract.OwnershipTransferCommand command,
                                 com.opensocket.aievent.core.resourceaccess.contract.OwnershipTransferResult result);
    Optional<ResourceOrphanRepairCase> findOpenOrphan(ResourceRef resourceRef);
    void markOrphanResolved(String repairId, ResourceRef resourceRef, OwnershipDescriptor ownership,
                            long resourceVersion, String actorId, Instant resolvedAt);
}
