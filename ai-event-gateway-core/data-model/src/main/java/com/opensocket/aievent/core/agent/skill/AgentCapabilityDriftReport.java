package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentCapabilityDriftReport {
    private String agentId;
    private String taxonomyVersion;
    private int scannedAgents;
    private int alignedCount;
    private int driftCount;
    private int highSeverityCount;
    private Map<String, Integer> driftTypeCounts = Map.of();
    private List<AgentCapabilityDriftItem> items = List.of();
    private OffsetDateTime generatedAt;
}
