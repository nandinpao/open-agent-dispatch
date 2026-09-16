package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Map; import java.util.Objects;
/** Human request to authorize one Provider-side effect before Issue Tracking chooses a Provider identity. */
public record ExternalWriteAuthorizationCommand(ResourceRef resourceRef, ResourceAction action, VisibilityLevel requestedVisibility,
 String purpose, boolean separationOfDutiesSatisfied, boolean stepUpSatisfied, String idempotencyKey, Map<String,String> trustedFlowContext) {
 public ExternalWriteAuthorizationCommand { Objects.requireNonNull(resourceRef,"resourceRef");Objects.requireNonNull(action,"action");
 requestedVisibility=requestedVisibility==null?VisibilityLevel.STANDARD:requestedVisibility; purpose=req(purpose,"purpose");
 idempotencyKey=req(idempotencyKey,"idempotencyKey"); trustedFlowContext=trustedFlowContext==null?Map.of():Map.copyOf(trustedFlowContext);}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
