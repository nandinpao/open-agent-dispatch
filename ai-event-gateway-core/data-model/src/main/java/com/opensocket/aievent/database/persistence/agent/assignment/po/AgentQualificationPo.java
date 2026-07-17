package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentQualificationPo {
    private String tenantId;
    private String qualificationId;
    private String agentId;
    private String profileCode;
    private String qualificationStatus;
    private String evidenceType;
    private String evidenceRef;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime expiresAt;
    private int grantedPolicyVersion;
    private OffsetDateTime lastRenewedAt;
    private OffsetDateTime renewalDueAt;
    private String renewalStatus;
    private String reason;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
