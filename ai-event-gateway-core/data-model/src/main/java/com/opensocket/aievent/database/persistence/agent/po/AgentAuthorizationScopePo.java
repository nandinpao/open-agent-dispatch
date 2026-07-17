package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentAuthorizationScopePo {
    private String scopeId;
    private String agentId;
    private String tenantId;
    private String systemCode;
    private String siteCode;
    private String eventType;
    private String taskType;
    private String dataClassificationLimit;
    private boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
