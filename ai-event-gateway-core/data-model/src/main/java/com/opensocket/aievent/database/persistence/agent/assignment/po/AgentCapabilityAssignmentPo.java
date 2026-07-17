package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentCapabilityAssignmentPo {
    private String tenantId;
    private String assignmentId;
    private String agentId;
    private String capabilityCode;
    private String capabilityName;
    private String status;
    private String source;
    private String requestedBy;
    private OffsetDateTime requestedAt;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String revokedBy;
    private OffsetDateTime revokedAt;
    private OffsetDateTime expiresAt;
    private String evidenceRef;
    private String reason;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
