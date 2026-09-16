package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
/** Human-readable Role Binding directory row. */
public record AccessAssignmentResponse(String bindingId,String principalType,String principalId,String principalName,String roleId,String roleCode,String roleName,String riskLevel,String scopeType,String scopeId,String scopeName,Instant effectiveAt,Instant expiresAt,String status,String lifecycleStatus,boolean reviewRequired,Instant nextReviewAt,long version){}
