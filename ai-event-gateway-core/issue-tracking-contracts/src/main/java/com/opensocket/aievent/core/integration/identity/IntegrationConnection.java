package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
/** Integration Connection authority with explicit Resource owner. */
public record IntegrationConnection(String tenantId,String connectionId,IntegrationProviderType providerType,String connectionName,
 String ownerDepartmentId,String ownerGroupId,String baseUrl,String deploymentType,String providerVersion,IntegrationConnectionStatus status,
 int timeoutMs,String retryPolicyId,String rateLimitPolicyId,String tlsPolicyId,boolean enabled,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {
 /** Compatibility constructor for pre-P4RA-F callers. Owner remains unresolved and therefore fail closed. */
 public IntegrationConnection(String tenantId,String connectionId,IntegrationProviderType providerType,String connectionName,String baseUrl,
  String deploymentType,String providerVersion,IntegrationConnectionStatus status,int timeoutMs,String retryPolicyId,String rateLimitPolicyId,
  String tlsPolicyId,boolean enabled,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){
  this(tenantId,connectionId,providerType,connectionName,null,null,baseUrl,deploymentType,providerVersion,status,timeoutMs,retryPolicyId,
   rateLimitPolicyId,tlsPolicyId,enabled,version,createdAt,updatedAt);
 }
}
