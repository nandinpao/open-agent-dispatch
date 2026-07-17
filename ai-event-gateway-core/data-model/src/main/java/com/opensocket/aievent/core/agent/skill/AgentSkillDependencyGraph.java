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
public class AgentSkillDependencyGraph {
    private String rootSkillCode;
    private String taxonomyVersion;
    private int depth;
    private List<String> nodes = List.of();
    private List<AgentSkillDependencyEdge> edges = List.of();
    private List<String> requiredSkillCodes = List.of();
    private List<String> replacementSkillCodes = List.of();
    private List<String> conflictSkillCodes = List.of();
    private boolean cycleDetected;
    private List<String> warnings = List.of();
    private Map<String, Object> metadata = Map.of();
    private OffsetDateTime generatedAt;
}
