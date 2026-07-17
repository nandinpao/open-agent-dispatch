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
public class AgentSkillRemediationProposal {
    private String agentId;
    private String taxonomyVersion;
    private int sourceDriftCount;
    private int highSeverityCount;
    private List<AgentSkillRemediationAction> actions = List.of();
    private List<String> summary = List.of();
    private Map<String, Object> metadata = Map.of();
    private OffsetDateTime generatedAt;
}
