package com.opensocket.aievent.core.resourceaccess.contract;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant; import java.util.Map; import java.util.Objects;

/** Human authorization evidence only. Provider principals, tokens and secrets are intentionally excluded. */
public record ExternalWriteAuthorizationContext(
 PrincipalRef humanPrincipal,String authorizationDecisionId,ResourceRef resourceRef,ResourceAction action,String purpose,
 boolean separationOfDutiesSatisfied,boolean stepUpSatisfied,boolean executable,String correlationId,Instant authorizedAt,
 Map<String,String> auditEvidence) {
 public ExternalWriteAuthorizationContext { Objects.requireNonNull(humanPrincipal,"humanPrincipal");authorizationDecisionId=req(authorizationDecisionId,"authorizationDecisionId");
 Objects.requireNonNull(resourceRef,"resourceRef");Objects.requireNonNull(action,"action");purpose=req(purpose,"purpose");
 correlationId=req(correlationId,"correlationId");Objects.requireNonNull(authorizedAt,"authorizedAt");auditEvidence=auditEvidence==null?Map.of():Map.copyOf(auditEvidence);}
 public ExternalWriteAuthorizationContext(PrincipalRef p,String d,ResourceRef r,ResourceAction a,String purpose,boolean sod,boolean step,String c,Instant at,Map<String,String> e){
  this(p,d,r,a,purpose,sod,step,true,c,at,e);}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
