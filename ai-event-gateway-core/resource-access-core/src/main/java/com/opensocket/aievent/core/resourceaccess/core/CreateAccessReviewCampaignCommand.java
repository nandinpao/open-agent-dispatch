package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record CreateAccessReviewCampaignCommand(
        String campaignId, String campaignName, String description,
        String resourceTypeFilter, String principalTypeFilter, Instant dueAt,
        String actorId, String correlationId, String idempotencyKey, Instant requestedAt) {
    public CreateAccessReviewCampaignCommand {
        campaignId=required(campaignId,"campaignId"); campaignName=required(campaignName,"campaignName");
        description=description==null?"":description.trim(); resourceTypeFilter=normalize(resourceTypeFilter);
        principalTypeFilter=normalize(principalTypeFilter); actorId=required(actorId,"actorId");
        correlationId=required(correlationId,"correlationId"); idempotencyKey=required(idempotencyKey,"idempotencyKey");
        if(dueAt==null||requestedAt==null)throw new IllegalArgumentException("dueAt and requestedAt are required");
        if(!dueAt.isAfter(requestedAt))throw new IllegalArgumentException("dueAt must be in the future");
    }
    private static String normalize(String v){return v==null?"":v.trim();}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
