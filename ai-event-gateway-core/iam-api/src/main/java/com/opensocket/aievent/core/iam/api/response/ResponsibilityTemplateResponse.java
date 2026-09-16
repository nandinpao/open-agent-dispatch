package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;import java.util.Set;
/** Business-facing projection of a canonical RBAC Role. */
public record ResponsibilityTemplateResponse(String roleId,String tenantId,String roleCode,String roleName,String description,String roleType,String status,boolean systemManaged,String riskLevel,boolean reviewRequired,Instant nextReviewAt,long permissionCount,long assignmentCount,long activeAssignmentCount,long expiringAssignmentCount,Set<String> allowedScopeTypes,Set<String> capabilityCodes,Set<String> allowedPrincipalTypes,long version){}
