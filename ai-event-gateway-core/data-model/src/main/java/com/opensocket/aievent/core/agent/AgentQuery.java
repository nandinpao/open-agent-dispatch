package com.opensocket.aievent.core.agent;

import java.util.List;

public class AgentQuery {
    private String siteId;
    private String ownerGatewayNodeId;
    private AgentStatus status;
    private List<String> requiredCapabilities = List.of();
    private boolean assignableOnly;
    private int limit = 100;

    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities); }
    public boolean isAssignableOnly() { return assignableOnly; }
    public void setAssignableOnly(boolean assignableOnly) { this.assignableOnly = assignableOnly; }
    public int getLimit() { return Math.max(1, Math.min(limit, 1000)); }
    public void setLimit(int limit) { this.limit = limit; }
}
