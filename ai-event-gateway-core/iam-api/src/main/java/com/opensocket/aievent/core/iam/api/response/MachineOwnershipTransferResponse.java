package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** Auditable result of an atomic Service Account / Agent ownership transfer. */
public record MachineOwnershipTransferResponse(
        String transferId,
        String tenantId,
        String fromUserId,
        String toUserId,
        long serviceAccountsUpdated,
        long agentBusinessOwnersUpdated,
        long agentTechnicalStewardsUpdated,
        String actorId,
        String reason,
        Instant transferredAt) {}
