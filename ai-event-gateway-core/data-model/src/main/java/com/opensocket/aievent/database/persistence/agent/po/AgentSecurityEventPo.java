package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSecurityEventPo {
    private String securityEventId;
    private String gatewayNodeId;
    private String claimedAgentId;
    private String agentId;
    private String eventType;
    private String reason;
    private String fingerprint;
    private String remoteAddress;
    private String metadataJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;
}
