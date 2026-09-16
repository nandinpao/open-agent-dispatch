package com.opensocket.aievent.core.issuetracking.change;
public record ProviderSyncResult(boolean success,boolean retryable,Integer statusCode,String providerObjectId,String evidenceReference,String reasonCode,String safeMessage) {
 public static ProviderSyncResult success(Integer status,String objectId,String evidence){return new ProviderSyncResult(true,false,status,objectId,evidence,"SYNCED","Provider synchronization succeeded.");}
 public static ProviderSyncResult failure(boolean retryable,Integer status,String code,String message){return new ProviderSyncResult(false,retryable,status,"","",code==null?"PROVIDER_SYNC_FAILED":code,message==null?"Provider synchronization failed.":message);}
}
