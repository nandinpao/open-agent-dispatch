package com.opensocket.aievent.core.iam.rbac.domain;

import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ShadowDecisionRecord(String shadowId,String tenantId,String principalId,String permission,String route,
                                   LegacyDecision legacyDecision,AuthorizationDecision.Effect newDecision,String reasonCode,
                                   ShadowLane lane,boolean highRisk,Map<String,String> metadata,Instant occurredAt){
    public ShadowDecisionRecord{shadowId=required(shadowId,"shadowId");tenantId=tenantId==null?"":tenantId.trim();principalId=required(principalId,"principalId");permission=required(permission,"permission");route=route==null?"":route.trim();Objects.requireNonNull(legacyDecision,"legacyDecision");Objects.requireNonNull(newDecision,"newDecision");reasonCode=required(reasonCode,"reasonCode");Objects.requireNonNull(lane,"lane");metadata=metadata==null?Map.of():Map.copyOf(metadata);Objects.requireNonNull(occurredAt,"occurredAt");}
    public boolean mismatch(){return (legacyDecision==LegacyDecision.ALLOW&&newDecision!=AuthorizationDecision.Effect.ALLOW)||(legacyDecision==LegacyDecision.DENY&&newDecision!=AuthorizationDecision.Effect.DENY)||legacyDecision==LegacyDecision.ERROR;}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
