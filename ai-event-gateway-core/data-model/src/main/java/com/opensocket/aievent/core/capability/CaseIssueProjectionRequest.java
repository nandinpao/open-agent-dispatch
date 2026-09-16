package com.opensocket.aievent.core.capability;
/** Records an external issue projection intent only. It performs no Jira/GitLab/Redmine/SOC I/O in Phase 9. */
public record CaseIssueProjectionRequest(String projectionId,String connectorType,String targetRef,boolean primaryProjection,String existingProjectionRef,String idempotencyKey){}
