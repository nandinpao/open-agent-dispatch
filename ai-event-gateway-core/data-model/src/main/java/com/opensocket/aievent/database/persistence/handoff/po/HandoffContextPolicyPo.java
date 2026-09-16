package com.opensocket.aievent.database.persistence.handoff.po;
import java.time.OffsetDateTime;
public class HandoffContextPolicyPo {
 private String tenantId;
 private String policyId;
 private String policyName;
 private String policyType;
 private String contextRequirement;
 private String defaultFieldDecision;
 private String attachmentPolicy;
 private String approvalMode;
 private String allowedFieldPathsJson;
 private String allowedCommentTypesJson;
 private String maskingRulesJson;
 private String resultSharingPolicy;
 private boolean enabled;
 private long version;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getPolicyId(){return policyId;} public void setPolicyId(String policyId){this.policyId=policyId;}
 public String getPolicyName(){return policyName;} public void setPolicyName(String policyName){this.policyName=policyName;}
 public String getPolicyType(){return policyType;} public void setPolicyType(String policyType){this.policyType=policyType;}
 public String getContextRequirement(){return contextRequirement;} public void setContextRequirement(String contextRequirement){this.contextRequirement=contextRequirement;}
 public String getDefaultFieldDecision(){return defaultFieldDecision;} public void setDefaultFieldDecision(String defaultFieldDecision){this.defaultFieldDecision=defaultFieldDecision;}
 public String getAttachmentPolicy(){return attachmentPolicy;} public void setAttachmentPolicy(String attachmentPolicy){this.attachmentPolicy=attachmentPolicy;}
 public String getApprovalMode(){return approvalMode;} public void setApprovalMode(String approvalMode){this.approvalMode=approvalMode;}
 public String getAllowedFieldPathsJson(){return allowedFieldPathsJson;} public void setAllowedFieldPathsJson(String allowedFieldPathsJson){this.allowedFieldPathsJson=allowedFieldPathsJson;}
 public String getAllowedCommentTypesJson(){return allowedCommentTypesJson;} public void setAllowedCommentTypesJson(String allowedCommentTypesJson){this.allowedCommentTypesJson=allowedCommentTypesJson;}
 public String getMaskingRulesJson(){return maskingRulesJson;} public void setMaskingRulesJson(String maskingRulesJson){this.maskingRulesJson=maskingRulesJson;}
 public String getResultSharingPolicy(){return resultSharingPolicy;} public void setResultSharingPolicy(String resultSharingPolicy){this.resultSharingPolicy=resultSharingPolicy;}
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
 public long getVersion(){return version;} public void setVersion(long version){this.version=version;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime createdAt){this.createdAt=createdAt;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime updatedAt){this.updatedAt=updatedAt;}
}
