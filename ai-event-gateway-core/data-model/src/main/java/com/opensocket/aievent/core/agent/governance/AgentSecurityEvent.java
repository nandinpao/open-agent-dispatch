package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentSecurityEvent {
    private String securityEventId;
    private String gatewayNodeId;
    private String claimedAgentId;
    private String agentId;
    private AgentSecurityEventType eventType;
    private String reason;
    private String fingerprint;
    private String remoteAddress;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;

    public String getSecurityEventId() { return securityEventId; }
    public void setSecurityEventId(String securityEventId) { this.securityEventId = securityEventId; }
    public String getGatewayNodeId() { return gatewayNodeId; }
    public void setGatewayNodeId(String gatewayNodeId) { this.gatewayNodeId = gatewayNodeId; }
    public String getClaimedAgentId() { return claimedAgentId; }
    public void setClaimedAgentId(String claimedAgentId) { this.claimedAgentId = claimedAgentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public AgentSecurityEventType getEventType() { return eventType; }
    public void setEventType(AgentSecurityEventType eventType) { this.eventType = eventType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getRemoteAddress() { return remoteAddress; }
    public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
