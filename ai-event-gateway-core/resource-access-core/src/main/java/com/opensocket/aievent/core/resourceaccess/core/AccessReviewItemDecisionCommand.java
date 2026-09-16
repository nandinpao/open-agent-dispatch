package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record AccessReviewItemDecisionCommand(
        String campaignId, String itemId, AccessReviewItemStatus decision,
        long expectedVersion, String actorId, String reason,
        String correlationId, String idempotencyKey, Instant requestedAt) {
    public AccessReviewItemDecisionCommand {
        campaignId=required(campaignId,"campaignId");itemId=required(itemId,"itemId");
        if(decision==null||decision==AccessReviewItemStatus.OPEN)throw new IllegalArgumentException("terminal review decision is required");
        if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");
        actorId=required(actorId,"actorId");reason=required(reason,"reason");correlationId=required(correlationId,"correlationId");idempotencyKey=required(idempotencyKey,"idempotencyKey");
        if(requestedAt==null)throw new IllegalArgumentException("requestedAt is required");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
