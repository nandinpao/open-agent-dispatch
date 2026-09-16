package com.opensocket.aievent.core.integration.identity;
import com.opensocket.aievent.core.issuetracking.identity.ProviderWriteIdentityPolicy;
import java.time.OffsetDateTime; import java.util.*;
public record IntegrationProjectMapping(
 String tenantId,String mappingId,String connectionId,String departmentId,String groupId,String serviceDomainId,String sourceSystemId,String taskType,
 String externalProjectId,String externalProjectKey,String externalIssueType,String externalTrackerId,
 String readPrincipalId,String createPrincipalId,String commentPrincipalId,String updatePrincipalId,String relationPrincipalId,String webhookPrincipalId,
 String permissionProfileId,String contextPolicyId,String resultSharingPolicyId,ProviderWriteIdentityPolicy providerWriteIdentityPolicy,
 ProjectMappingStatus mappingStatus,int resolutionPriority, boolean defaultMapping,boolean enabled,ProjectMappingLifecycle lifecycleStatus,int mappingVersion,
 String summaryTemplate,String descriptionTemplate,List<String> requiredFields,Map<String,String> customFieldMappings,Map<String,String> transitionMappings,
 String commentPolicy,String linkPolicy,String metadataSnapshotId,String metadataSchemaHash,OffsetDateTime validatedAt,OffsetDateTime publishedAt,
 Integer supersedesMappingVersion,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {
 public IntegrationProjectMapping {
  providerWriteIdentityPolicy=providerWriteIdentityPolicy==null?ProviderWriteIdentityPolicy.SERVICE_ACCOUNT_ON_BEHALF_OF:providerWriteIdentityPolicy;
  lifecycleStatus=lifecycleStatus==null?(enabled?ProjectMappingLifecycle.ACTIVE:ProjectMappingLifecycle.DRAFT):lifecycleStatus;
  mappingVersion=mappingVersion<=0?1:mappingVersion; summaryTemplate=summaryTemplate==null?"{{task.title}}":summaryTemplate;
  descriptionTemplate=descriptionTemplate==null?"{{task.description}}":descriptionTemplate;
  requiredFields=requiredFields==null?List.of():List.copyOf(requiredFields); customFieldMappings=customFieldMappings==null?Map.of():Map.copyOf(customFieldMappings);
  transitionMappings=transitionMappings==null?Map.of():Map.copyOf(transitionMappings); commentPolicy=commentPolicy==null?"APPEND_ONLY":commentPolicy;
  linkPolicy=linkPolicy==null?"CANONICAL_ONLY":linkPolicy;
 }
 /** Compatibility full constructor defaults Provider writes to Service Account On-Behalf-Of. */
 public IntegrationProjectMapping(String tenantId,String mappingId,String connectionId,String departmentId,String groupId,String serviceDomainId,String sourceSystemId,String taskType,
 String externalProjectId,String externalProjectKey,String externalIssueType,String externalTrackerId,String readPrincipalId,String createPrincipalId,String commentPrincipalId,
 String updatePrincipalId,String relationPrincipalId,String webhookPrincipalId,String permissionProfileId,String contextPolicyId,String resultSharingPolicyId,
 ProjectMappingStatus mappingStatus,int resolutionPriority,boolean defaultMapping,boolean enabled,ProjectMappingLifecycle lifecycleStatus,int mappingVersion,
 String summaryTemplate,String descriptionTemplate,List<String> requiredFields,Map<String,String> customFieldMappings,Map<String,String> transitionMappings,
 String commentPolicy,String linkPolicy,String metadataSnapshotId,String metadataSchemaHash,OffsetDateTime validatedAt,OffsetDateTime publishedAt,
 Integer supersedesMappingVersion,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){
  this(tenantId,mappingId,connectionId,departmentId,groupId,serviceDomainId,sourceSystemId,taskType,externalProjectId,externalProjectKey,externalIssueType,
   externalTrackerId,readPrincipalId,createPrincipalId,commentPrincipalId,updatePrincipalId,relationPrincipalId,webhookPrincipalId,permissionProfileId,
   contextPolicyId,resultSharingPolicyId,ProviderWriteIdentityPolicy.SERVICE_ACCOUNT_ON_BEHALF_OF,mappingStatus,resolutionPriority,defaultMapping,enabled,
   lifecycleStatus,mappingVersion,summaryTemplate,descriptionTemplate,requiredFields,customFieldMappings,transitionMappings,commentPolicy,linkPolicy,
   metadataSnapshotId,metadataSchemaHash,validatedAt,publishedAt,supersedesMappingVersion,version,createdAt,updatedAt);
 }
 public IntegrationProjectMapping(String tenantId,String mappingId,String connectionId,String departmentId,String groupId,String serviceDomainId,String sourceSystemId,String taskType,
 String externalProjectId,String externalProjectKey,String externalIssueType,String externalTrackerId,String readPrincipalId,String createPrincipalId,String commentPrincipalId,
 String updatePrincipalId,String relationPrincipalId,String webhookPrincipalId,String permissionProfileId,String contextPolicyId,String resultSharingPolicyId,
 ProjectMappingStatus mappingStatus,int resolutionPriority,boolean defaultMapping,boolean enabled,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){
  this(tenantId,mappingId,connectionId,departmentId,groupId,serviceDomainId,sourceSystemId,taskType,externalProjectId,externalProjectKey,externalIssueType,
   externalTrackerId,readPrincipalId,createPrincipalId,commentPrincipalId,updatePrincipalId,relationPrincipalId,webhookPrincipalId,permissionProfileId,
   contextPolicyId,resultSharingPolicyId,ProviderWriteIdentityPolicy.SERVICE_ACCOUNT_ON_BEHALF_OF,mappingStatus,resolutionPriority,defaultMapping,enabled,
   enabled?ProjectMappingLifecycle.ACTIVE:ProjectMappingLifecycle.DRAFT,1,"{{task.title}}","{{task.description}}",List.of(),Map.of(),Map.of(),
   "APPEND_ONLY","CANONICAL_ONLY",null,null,null,null,null,version,createdAt,updatedAt);
 }
 public boolean mutable(){return lifecycleStatus==ProjectMappingLifecycle.DRAFT||lifecycleStatus==ProjectMappingLifecycle.VALIDATING||lifecycleStatus==ProjectMappingLifecycle.VALID;}
}
