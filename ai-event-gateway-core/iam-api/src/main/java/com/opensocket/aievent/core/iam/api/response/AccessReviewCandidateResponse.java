package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
/** Access assignment selected by the canonical access-review policy. */
public record AccessReviewCandidateResponse(String bindingId,String principalType,String principalId,String principalName,String roleId,String roleCode,String roleName,String riskLevel,String scopeType,String scopeId,String scopeName,Instant effectiveAt,Instant expiresAt,Instant nextReviewAt,Instant lastReviewedAt,String reviewReason,long version){}
