package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentCapabilityAssignment {
    private String tenantId;
    private String assignmentId;
    private String agentId;
    private String capabilityCode;
    private String capabilityName;
    private AgentCapabilityAssignmentStatus status = AgentCapabilityAssignmentStatus.PENDING_APPROVAL;
    private String source = "MANUAL";
    private String requestedBy;
    private OffsetDateTime requestedAt;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String revokedBy;
    private OffsetDateTime revokedAt;
    private OffsetDateTime expiresAt;
    private String evidenceRef;
    private String reason;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
