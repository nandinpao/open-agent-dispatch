package com.opensocket.aievent.core.issuetracking.background;
/** Controlled bridge used by Issue Relay/Recovery around external or state-changing background side effects. */
public interface IssueBackgroundAuthorizationPort {
 IssueBackgroundExecutionAuthorization authorize(String tenantId,String jobCode,String resourceType,String resourceId,String permissionCode,String purpose,String correlationId);
 IssueBackgroundExecutionAuthorization checkpoint(IssueBackgroundExecutionAuthorization authorization,String operationPhase,String correlationId);
 void complete(IssueBackgroundExecutionAuthorization authorization,String correlationId);
 void revoke(IssueBackgroundExecutionAuthorization authorization,String reasonCode,String correlationId);
}
