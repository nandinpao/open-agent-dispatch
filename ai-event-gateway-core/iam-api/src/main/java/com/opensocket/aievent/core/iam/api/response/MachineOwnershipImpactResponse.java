package com.opensocket.aievent.core.iam.api.response;

/** Accountable machine/Agent ownership that must be reviewed before a Person is suspended or disabled. */
public record MachineOwnershipImpactResponse(
        long serviceAccountCount,
        long agentBusinessOwnerCount,
        long agentTechnicalStewardCount,
        long ownershipReviewRequiredCount,
        boolean hasBlockingOwnership) {
}
