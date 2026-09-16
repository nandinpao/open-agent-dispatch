package com.opensocket.aievent.database.persistence.issuechange.po;
import java.time.OffsetDateTime;

public class ExternalChangePolicyPo {
 private String tenantId;
 private String policyId;
 private String name;
 private String connectionId;
 private String projectMappingId;
 private String externalProjectId;
 private String issueType;
 private String resourceType;
 private String fieldPath;
 private String direction;
 private String action;
 private String riskLevel;
 private boolean requiresReauthentication;
 private boolean requiresApproval;
 private boolean allowProviderMutation;
 private int priority;
 private String status;
 private long version;
 private OffsetDateTime effectiveFrom;
 private OffsetDateTime expiresAt;
 private OffsetDateTime updatedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getPolicyId(){return policyId;} public void setPolicyId(String value){this.policyId=value;}
 public String getName(){return name;} public void setName(String value){this.name=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String value){this.projectMappingId=value;}
 public String getExternalProjectId(){return externalProjectId;} public void setExternalProjectId(String value){this.externalProjectId=value;}
 public String getIssueType(){return issueType;} public void setIssueType(String value){this.issueType=value;}
 public String getResourceType(){return resourceType;} public void setResourceType(String value){this.resourceType=value;}
 public String getFieldPath(){return fieldPath;} public void setFieldPath(String value){this.fieldPath=value;}
 public String getDirection(){return direction;} public void setDirection(String value){this.direction=value;}
 public String getAction(){return action;} public void setAction(String value){this.action=value;}
 public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String value){this.riskLevel=value;}
 public boolean isRequiresReauthentication(){return requiresReauthentication;} public void setRequiresReauthentication(boolean value){this.requiresReauthentication=value;}
 public boolean isRequiresApproval(){return requiresApproval;} public void setRequiresApproval(boolean value){this.requiresApproval=value;}
 public boolean isAllowProviderMutation(){return allowProviderMutation;} public void setAllowProviderMutation(boolean value){this.allowProviderMutation=value;}
 public int getPriority(){return priority;} public void setPriority(int value){this.priority=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public OffsetDateTime getEffectiveFrom(){return effectiveFrom;} public void setEffectiveFrom(OffsetDateTime value){this.effectiveFrom=value;}
 public OffsetDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(OffsetDateTime value){this.expiresAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
}
