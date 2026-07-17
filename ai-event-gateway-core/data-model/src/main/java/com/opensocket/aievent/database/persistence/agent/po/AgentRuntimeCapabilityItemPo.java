package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeCapabilityItemPo {
    private String agentId;
    private String capabilityKind;
    private String capabilityValue;
    private String capabilityRevision;
    private String source;
    private OffsetDateTime updatedAt;
}
