package com.opensocket.aievent.core.integration.issue;
public record ProjectionProviderReadbackResult(ProjectionReadbackStatus status,String externalIssueId,
 String externalIssueKey,String externalIssueUrl,String externalIssueStatus,String observedFingerprint,
 String reasonCode,String safeSummary) {
 public static ProjectionProviderReadbackResult notFound(String reason){return new ProjectionProviderReadbackResult(ProjectionReadbackStatus.NOT_FOUND,null,null,null,null,null,"PROVIDER_MARKER_NOT_FOUND",reason);}
 public static ProjectionProviderReadbackResult matched(String id,String key,String url,String issueStatus,String fingerprint,String summary){return new ProjectionProviderReadbackResult(ProjectionReadbackStatus.MATCHED,id,key,url,issueStatus,fingerprint,null,summary);}
}
