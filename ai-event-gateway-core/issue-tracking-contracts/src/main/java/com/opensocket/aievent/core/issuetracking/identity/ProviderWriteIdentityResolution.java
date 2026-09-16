package com.opensocket.aievent.core.issuetracking.identity;
/** Resolved Provider identity metadata. Secret references and secret values are intentionally excluded. */
public record ProviderWriteIdentityResolution(ProviderWriteIdentityPolicy policy,String connectionId,String mappingId,
 String integrationPrincipalId,String credentialId,String credentialVersion,String providerActorId,boolean delegated) {
 public ProviderWriteIdentityResolution {if(policy==null)throw new IllegalArgumentException("policy is required");connectionId=req(connectionId,"connectionId");
 mappingId=req(mappingId,"mappingId");integrationPrincipalId=req(integrationPrincipalId,"integrationPrincipalId");credentialId=req(credentialId,"credentialId");
 credentialVersion=req(credentialVersion,"credentialVersion");providerActorId=providerActorId==null?"":providerActorId.trim();}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
