package com.opensocket.aievent.core.routing.cutover;

import java.time.OffsetDateTime;

public class DispatchCutoverOutcome {
    private String tenantId;
    private String outcomeId;
    private String taskId;
    private String flowId;
    private String policyId;
    private boolean authoritative;
    private boolean requirementBlocked;
    private boolean noCandidate;
    private boolean selectedAgentDifferent;
    private String selectedAgentId;
    private String legacySelectedAgentId;
    private String reasonCode;
    private OffsetDateTime createdAt;
    public void validate(){require(tenantId,"tenantId");require(outcomeId,"outcomeId");require(taskId,"taskId");require(flowId,"flowId");}
    private static void require(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getOutcomeId(){return outcomeId;} public void setOutcomeId(String v){outcomeId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getFlowId(){return flowId;} public void setFlowId(String v){flowId=v;}
    public String getPolicyId(){return policyId;} public void setPolicyId(String v){policyId=v;}
    public boolean isAuthoritative(){return authoritative;} public void setAuthoritative(boolean v){authoritative=v;}
    public boolean isRequirementBlocked(){return requirementBlocked;} public void setRequirementBlocked(boolean v){requirementBlocked=v;}
    public boolean isNoCandidate(){return noCandidate;} public void setNoCandidate(boolean v){noCandidate=v;}
    public boolean isSelectedAgentDifferent(){return selectedAgentDifferent;} public void setSelectedAgentDifferent(boolean v){selectedAgentDifferent=v;}
    public String getSelectedAgentId(){return selectedAgentId;} public void setSelectedAgentId(String v){selectedAgentId=v;}
    public String getLegacySelectedAgentId(){return legacySelectedAgentId;} public void setLegacySelectedAgentId(String v){legacySelectedAgentId=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
}
