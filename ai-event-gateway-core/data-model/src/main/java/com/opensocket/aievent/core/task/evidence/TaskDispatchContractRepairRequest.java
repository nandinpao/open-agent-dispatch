package com.opensocket.aievent.core.task.evidence;

public class TaskDispatchContractRepairRequest {
    private String agentId;
    private String capabilityCode;
    private String profileCode;
    private String policyCode;
    private String operatorId;
    private boolean assignAgent;
    private boolean approveAgentQualification = true;
    private boolean approveAgentCapability = true;
    private boolean activate = true;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getCapabilityCode() { return capabilityCode; }
    public void setCapabilityCode(String capabilityCode) { this.capabilityCode = capabilityCode; }
    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public boolean isAssignAgent() { return assignAgent; }
    public void setAssignAgent(boolean assignAgent) { this.assignAgent = assignAgent; }
    public boolean isApproveAgentQualification() { return approveAgentQualification; }
    public void setApproveAgentQualification(boolean approveAgentQualification) { this.approveAgentQualification = approveAgentQualification; }
    public boolean isApproveAgentCapability() { return approveAgentCapability; }
    public void setApproveAgentCapability(boolean approveAgentCapability) { this.approveAgentCapability = approveAgentCapability; }
    public boolean isActivate() { return activate; }
    public void setActivate(boolean activate) { this.activate = activate; }
}
