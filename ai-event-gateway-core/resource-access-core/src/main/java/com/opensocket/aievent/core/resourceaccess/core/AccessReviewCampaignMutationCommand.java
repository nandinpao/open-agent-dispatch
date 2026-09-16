package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record AccessReviewCampaignMutationCommand(
        String campaignId, long expectedVersion, String actorId, String reason,
        String correlationId, String idempotencyKey, Instant requestedAt) {
    public AccessReviewCampaignMutationCommand {
        campaignId=required(campaignId,"campaignId");if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");
        actorId=required(actorId,"actorId");reason=required(reason,"reason");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");
        if(requestedAt==null)throw new IllegalArgumentException("requestedAt is required");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
