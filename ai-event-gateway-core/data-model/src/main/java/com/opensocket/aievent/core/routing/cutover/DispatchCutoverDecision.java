package com.opensocket.aievent.core.routing.cutover;

import java.time.OffsetDateTime;

public class DispatchCutoverDecision {
    private String tenantId;
    private String decisionId;
    private String taskId;
    private String flowId;
    private String policyId;
    private DispatchCutoverMode configuredMode;
    private boolean authoritative;
    private int deterministicBucket;
    private String reasonCode;
    private OffsetDateTime createdAt;

    public void validate() {
        require(tenantId, "tenantId"); require(decisionId, "decisionId"); require(taskId, "taskId"); require(flowId, "flowId");
        if (configuredMode == null) throw new IllegalArgumentException("configuredMode is required");
        if (deterministicBucket < 0 || deterministicBucket > 99) throw new IllegalArgumentException("deterministicBucket must be 0..99");
    }
    private static void require(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getDecisionId(){return decisionId;} public void setDecisionId(String v){decisionId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getFlowId(){return flowId;} public void setFlowId(String v){flowId=v;}
    public String getPolicyId(){return policyId;} public void setPolicyId(String v){policyId=v;}
    public DispatchCutoverMode getConfiguredMode(){return configuredMode;} public void setConfiguredMode(DispatchCutoverMode v){configuredMode=v;}
    public boolean isAuthoritative(){return authoritative;} public void setAuthoritative(boolean v){authoritative=v;}
    public int getDeterministicBucket(){return deterministicBucket;} public void setDeterministicBucket(int v){deterministicBucket=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
}
