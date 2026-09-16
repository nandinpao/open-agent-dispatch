package com.opensocket.aievent.database.persistence.handoff.po;

public class HandoffContextFieldDecisionPo {
 private String tenantId;
 private String snapshotId;
 private String fieldPath;
 private String classification;
 private String sensitivityLevel;
 private String shareDecision;
 private String maskingMethod;
 private String sourceReference;
 private String projectedValueJson;
 private String originalValueHash;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
 public String getFieldPath(){return fieldPath;} public void setFieldPath(String fieldPath){this.fieldPath=fieldPath;}
 public String getClassification(){return classification;} public void setClassification(String classification){this.classification=classification;}
 public String getSensitivityLevel(){return sensitivityLevel;} public void setSensitivityLevel(String sensitivityLevel){this.sensitivityLevel=sensitivityLevel;}
 public String getShareDecision(){return shareDecision;} public void setShareDecision(String shareDecision){this.shareDecision=shareDecision;}
 public String getMaskingMethod(){return maskingMethod;} public void setMaskingMethod(String maskingMethod){this.maskingMethod=maskingMethod;}
 public String getSourceReference(){return sourceReference;} public void setSourceReference(String sourceReference){this.sourceReference=sourceReference;}
 public String getProjectedValueJson(){return projectedValueJson;} public void setProjectedValueJson(String projectedValueJson){this.projectedValueJson=projectedValueJson;}
 public String getOriginalValueHash(){return originalValueHash;} public void setOriginalValueHash(String originalValueHash){this.originalValueHash=originalValueHash;}
}
