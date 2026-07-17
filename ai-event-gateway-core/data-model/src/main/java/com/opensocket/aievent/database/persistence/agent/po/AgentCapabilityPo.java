package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentCapabilityPo {
    private String agentId;
    private String capabilityCode;
    private String capabilityVersion;
    private boolean enabled;
    private String approvedBy;
    private OffsetDateTime approvedAt;
}
