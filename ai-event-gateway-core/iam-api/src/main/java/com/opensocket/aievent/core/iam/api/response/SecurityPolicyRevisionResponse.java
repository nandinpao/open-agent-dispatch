package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
public record SecurityPolicyRevisionResponse(String revisionId,String tenantId,String policyKind,long policyVersion,String policyJson,String actorId,String auditReason,String correlationId,Instant createdAt){}
