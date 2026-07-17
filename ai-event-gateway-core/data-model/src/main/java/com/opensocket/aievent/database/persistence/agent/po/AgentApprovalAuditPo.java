package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentApprovalAuditPo {
    private String auditId;
    private String agentId;
    private String enrollmentId;
    private String action;
    private String oldStatus;
    private String newStatus;
    private String operatorId;
    private String reason;
    private OffsetDateTime createdAt;
}
