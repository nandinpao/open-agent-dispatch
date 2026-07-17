package com.opensocket.aievent.core.agent.governance;

import java.util.ArrayList;
import java.util.List;

/**
 * Security remediation command raised when one Agent identity is observed with
 * more than one runtime session across Netty gateway nodes.
 */
public class AgentDuplicateRuntimeSecurityCommand {
    private String operatorId;
    private String reason;
    private List<String> gatewayNodeIds = new ArrayList<>();
    private Integer connectedCount;
    private boolean disconnectAll = true;
    private boolean requireCredentialRotation = true;
    private boolean revokeCredentials;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getGatewayNodeIds() { return gatewayNodeIds; }
    public void setGatewayNodeIds(List<String> gatewayNodeIds) { this.gatewayNodeIds = gatewayNodeIds == null ? new ArrayList<>() : new ArrayList<>(gatewayNodeIds); }
    public Integer getConnectedCount() { return connectedCount; }
    public void setConnectedCount(Integer connectedCount) { this.connectedCount = connectedCount; }
    public boolean isDisconnectAll() { return disconnectAll; }
    public void setDisconnectAll(boolean disconnectAll) { this.disconnectAll = disconnectAll; }
    public boolean isRequireCredentialRotation() { return requireCredentialRotation; }
    public void setRequireCredentialRotation(boolean requireCredentialRotation) { this.requireCredentialRotation = requireCredentialRotation; }
    public boolean isRevokeCredentials() { return revokeCredentials; }
    public void setRevokeCredentials(boolean revokeCredentials) { this.revokeCredentials = revokeCredentials; }
}
