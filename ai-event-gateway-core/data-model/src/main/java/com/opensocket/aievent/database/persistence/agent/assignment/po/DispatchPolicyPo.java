package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchPolicyPo {
    private String tenantId;
    private String policyId;
    private String policyCode;
    private String policyName;
    private String description;
    private String ownerTeam;
    private String riskLevel;
    private String status;
    private int version;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
