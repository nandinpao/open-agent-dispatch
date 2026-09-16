package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

public record OrphanRepairCommand(
        String repairId,
        OwnershipTransferCommand ownershipTransfer,
        String repairReason,
        Instant requestedAt) {
    public OrphanRepairCommand {
        if (repairId == null || repairId.isBlank()) throw new IllegalArgumentException("repairId is required");
        repairId = repairId.trim();
        Objects.requireNonNull(ownershipTransfer, "ownershipTransfer");
        if (repairReason == null || repairReason.isBlank()) throw new IllegalArgumentException("repairReason is required");
        repairReason = repairReason.trim();
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
