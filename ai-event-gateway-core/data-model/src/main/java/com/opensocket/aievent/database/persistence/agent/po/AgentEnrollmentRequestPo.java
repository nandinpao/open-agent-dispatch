package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentEnrollmentRequestPo {
    private String enrollmentId;
    private String claimedAgentId;
    private String tenantId;
    private String agentName;
    private String agentType;
    private String submittedMetadataJson;
    private String evidenceJson;
    private String fingerprint;
    private String remoteAddress;
    private String status;
    private OffsetDateTime submittedAt;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String reviewComment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
