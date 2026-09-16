package com.opensocket.aievent.core.resourceaccess.contract;
/** Immutable decision evidence wrapper. */
public record AuthorizationDecisionAuditRecord(AuthorizationRequest request,AuthorizationDecision decision,String descriptorHash){
 public AuthorizationDecisionAuditRecord{if(request==null)throw new IllegalArgumentException("request is required");if(decision==null)throw new IllegalArgumentException("decision is required");descriptorHash=descriptorHash==null?"":descriptorHash.trim();}
}
