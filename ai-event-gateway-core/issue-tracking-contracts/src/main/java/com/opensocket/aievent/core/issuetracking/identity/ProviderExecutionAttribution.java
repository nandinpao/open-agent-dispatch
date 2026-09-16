package com.opensocket.aievent.core.issuetracking.identity;
import java.time.OffsetDateTime;
/** Immutable dual-actor audit linking Human authorization to the Provider execution identity. */
public record ProviderExecutionAttribution(String tenantId,String attributionId,String humanPrincipalId,String authorizationDecisionId,
 String resourceType,String resourceId,String resourceAction,String purpose,String connectionId,String mappingId,
 String integrationPrincipalId,String credentialId,String credentialVersion,String providerActorId,ProviderWriteIdentityPolicy writeIdentityPolicy,
 ProviderExecutionAttributionStatus status,String correlationId,String idempotencyKey,String providerOperationId,String failureCode,OffsetDateTime createdAt) {
 public ProviderExecutionAttribution {tenantId=req(tenantId,"tenantId");attributionId=req(attributionId,"attributionId");humanPrincipalId=req(humanPrincipalId,"humanPrincipalId");
 authorizationDecisionId=req(authorizationDecisionId,"authorizationDecisionId");resourceType=req(resourceType,"resourceType");resourceId=req(resourceId,"resourceId");
 resourceAction=req(resourceAction,"resourceAction");purpose=req(purpose,"purpose");connectionId=req(connectionId,"connectionId");mappingId=req(mappingId,"mappingId");
 integrationPrincipalId=req(integrationPrincipalId,"integrationPrincipalId");credentialId=req(credentialId,"credentialId");credentialVersion=req(credentialVersion,"credentialVersion");
 providerActorId=providerActorId==null?"":providerActorId.trim();if(writeIdentityPolicy==null)throw new IllegalArgumentException("writeIdentityPolicy is required");
 if(status==null)throw new IllegalArgumentException("status is required");correlationId=req(correlationId,"correlationId");idempotencyKey=req(idempotencyKey,"idempotencyKey");
 providerOperationId=providerOperationId==null?"":providerOperationId.trim();failureCode=failureCode==null?"":failureCode.trim();if(createdAt==null)throw new IllegalArgumentException("createdAt is required");}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
