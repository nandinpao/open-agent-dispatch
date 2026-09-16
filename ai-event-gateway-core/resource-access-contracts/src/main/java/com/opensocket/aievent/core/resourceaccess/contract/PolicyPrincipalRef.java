package com.opensocket.aievent.core.resourceaccess.contract;
/** Principal identity used when matching Resource Access policy rows. */
public record PolicyPrincipalRef(ScopePrincipalType principalType,String principalId){
 public PolicyPrincipalRef{if(principalType==null)throw new IllegalArgumentException("principalType is required");if(principalId==null||principalId.isBlank())throw new IllegalArgumentException("principalId is required");principalId=principalId.trim();}
}
