package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeFeatureObservationPo {
    private String tenantId;
    private String observationId;
    private String agentId;
    private String featureCode;
    private String featureName;
    private String observedValue;
    private String source;
    private String probeResult;
    private OffsetDateTime observedAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
