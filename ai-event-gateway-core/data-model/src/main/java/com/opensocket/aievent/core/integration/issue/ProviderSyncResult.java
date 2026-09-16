package com.opensocket.aievent.core.integration.issue;
public record ProviderSyncResult(boolean success,boolean retryable,Integer providerStatus,String externalIssueId,String externalIssueKey,String externalIssueUrl,String externalIssueStatus,String providerRelationId,String responseSummary,String errorCode,String errorMessage) {
 public static ProviderSyncResult success(Integer status,String id,String key,String url,String issueStatus,String relation,String summary){return new ProviderSyncResult(true,false,status,id,key,url,issueStatus,relation,summary,null,null);}
 public static ProviderSyncResult failure(boolean retryable,Integer status,String code,String message){return new ProviderSyncResult(false,retryable,status,null,null,null,null,null,null,code,message);}
}
