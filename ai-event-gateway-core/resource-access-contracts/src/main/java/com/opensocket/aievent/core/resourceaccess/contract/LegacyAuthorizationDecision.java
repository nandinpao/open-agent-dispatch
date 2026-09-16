package com.opensocket.aievent.core.resourceaccess.contract;
/** Legacy access result captured only for shadow comparison. */
public record LegacyAuthorizationDecision(Effect effect,String reasonCode,String decisionId){
 public LegacyAuthorizationDecision{if(effect==null)throw new IllegalArgumentException("effect is required");reasonCode=req(reasonCode,"reasonCode");decisionId=decisionId==null?"":decisionId.trim();}
 public enum Effect{ALLOW,DENY,NOT_AVAILABLE,ERROR}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
