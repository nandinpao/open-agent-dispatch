package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillAuditEntryPo {
    private String auditId;
    private String skillCode;
    private int version;
    private String action;
    private String operatorId;
    private String reason;
    private String fromStatus;
    private String toStatus;
    private String metadataJson;
    private OffsetDateTime createdAt;

}
