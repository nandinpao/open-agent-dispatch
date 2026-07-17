package com.opensocket.aievent.core.agent.governance;

/**
 * Marks a duplicate-runtime security case as remediated after a new credential
 * has been deployed and old runtime sessions have been disconnected.
 */
public class AgentDuplicateRuntimeResolveCommand {
    private String operatorId;
    private String reason;
    private Boolean enableAfterRotation = true;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getEnableAfterRotation() { return enableAfterRotation; }
    public void setEnableAfterRotation(Boolean enableAfterRotation) { this.enableAfterRotation = enableAfterRotation; }
}
