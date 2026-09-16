package com.opensocket.aievent.core.issuetracking.relay;
public record RelayProviderResult(boolean success,boolean retryable,String providerObjectId,String reasonCode,String safeMessage) {
 public static RelayProviderResult success(String providerObjectId){return new RelayProviderResult(true,false,providerObjectId==null?"":providerObjectId,"SYNCED","");}
 public static RelayProviderResult failure(boolean retryable,String reasonCode,String safeMessage){return new RelayProviderResult(false,retryable,"",reasonCode==null?"RELAY_PROVIDER_FAILURE":reasonCode,safeMessage==null?"Provider relay failed.":safeMessage);}
}
