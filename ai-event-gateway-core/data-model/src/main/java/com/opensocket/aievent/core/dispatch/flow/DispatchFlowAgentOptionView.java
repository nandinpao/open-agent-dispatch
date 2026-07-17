package com.opensocket.aievent.core.dispatch.flow;

/**
 * Agent option returned by the Dispatch Flow editor.
 *
 * <p>This is intentionally sourced from the same tenant-scoped Agent profile and
 * flow_agent_assignments data that Runtime uses for direct Dispatch Flow routing.
 * The UI must not infer selectability from a generic /admin/agents list.</p>
 */
public class DispatchFlowAgentOptionView {
    private String tenantId;
    private String agentId;
    private String agentName;
    private String approvalStatus;
    private Boolean enabled = false;
    private String riskStatus;
    private String runtimeStatus;
    private Boolean runtimeConnected = false;
    private Boolean heartbeatHealthy = false;
    private Boolean capacityAvailable = true;
    private Integer activeFlowCount = 0;
    private Boolean selectable = false;
    private String disabledReason;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled == null ? Boolean.FALSE : enabled; }
    public String getRiskStatus() { return riskStatus; }
    public void setRiskStatus(String riskStatus) { this.riskStatus = riskStatus; }
    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    public Boolean getRuntimeConnected() { return runtimeConnected; }
    public void setRuntimeConnected(Boolean runtimeConnected) { this.runtimeConnected = runtimeConnected == null ? Boolean.FALSE : runtimeConnected; }
    public Boolean getHeartbeatHealthy() { return heartbeatHealthy; }
    public void setHeartbeatHealthy(Boolean heartbeatHealthy) { this.heartbeatHealthy = heartbeatHealthy == null ? Boolean.FALSE : heartbeatHealthy; }
    public Boolean getCapacityAvailable() { return capacityAvailable; }
    public void setCapacityAvailable(Boolean capacityAvailable) { this.capacityAvailable = capacityAvailable == null ? Boolean.TRUE : capacityAvailable; }
    public Integer getActiveFlowCount() { return activeFlowCount; }
    public void setActiveFlowCount(Integer activeFlowCount) { this.activeFlowCount = activeFlowCount == null ? 0 : Math.max(0, activeFlowCount); }
    public Boolean getSelectable() { return selectable; }
    public void setSelectable(Boolean selectable) { this.selectable = selectable == null ? Boolean.FALSE : selectable; }
    public String getDisabledReason() { return disabledReason; }
    public void setDisabledReason(String disabledReason) { this.disabledReason = disabledReason; }
}
