package com.opensocket.aievent.core.integration.issue.projection;
public record IssueRelayProviderResult(boolean success,boolean retryable,boolean nativeRelationSupported,Integer providerStatus,String externalIssueId,String externalIssueUrl,String responseSummary,String errorCode) {
 public static IssueRelayProviderResult success(String issueId,String issueUrl,String summary){return new IssueRelayProviderResult(true,false,true,200,issueId,issueUrl,summary,null);} public static IssueRelayProviderResult failure(boolean retryable,Integer status,String code,String summary){return new IssueRelayProviderResult(false,retryable,false,status,null,null,summary,code);}
}
