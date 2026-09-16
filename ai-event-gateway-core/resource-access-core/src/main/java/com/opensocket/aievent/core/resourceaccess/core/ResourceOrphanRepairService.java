package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;

/** Repairs an orphan only through the Domain ownership authority and closes the projection repair case afterward. */
public final class ResourceOrphanRepairService {
    private final ResourceProjectionRepository repository; private final ResourceOwnershipTransferService transfers;
    public ResourceOrphanRepairService(ResourceProjectionRepository repository,ResourceOwnershipTransferService transfers){this.repository=Objects.requireNonNull(repository);this.transfers=Objects.requireNonNull(transfers);}
    public OrphanRepairResult repair(OrphanRepairCommand command){
        ResourceRef ref=command.ownershipTransfer().resourceRef();
        ResourceOrphanRepairCase open=repository.findOpenOrphan(ref).orElseThrow(()->new IllegalStateException("RESOURCE_ORPHAN_REPAIR_NOT_OPEN"));
        if(!open.repairId().equals(command.repairId()))throw new IllegalArgumentException("RESOURCE_ORPHAN_REPAIR_ID_MISMATCH");
        OwnershipTransferResult transfer=transfers.transfer(command.ownershipTransfer());
        repository.markOrphanResolved(command.repairId(),ref,transfer.currentOwnership(),transfer.resourceVersion(),command.ownershipTransfer().actorId(),transfer.transferredAt());
        return new OrphanRepairResult(command.repairId(),ref,transfer.currentOwnership(),transfer.resourceVersion(),"RESOLVED",transfer.transferredAt());
    }
}
