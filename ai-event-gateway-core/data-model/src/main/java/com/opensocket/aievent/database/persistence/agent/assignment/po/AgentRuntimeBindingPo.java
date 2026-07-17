package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeBindingPo {
    private String tenantId;
    private String bindingId;
    private String agentId;
    private String runtimeId;
    private String runtimeCode;
    private String bindingStatus;
    private String verifiedBy;
    private OffsetDateTime verifiedAt;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime expiresAt;
    private int capacityLimit;
    private String region;
    private String zone;
    private String dataScope;
    private String riskLimit;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
