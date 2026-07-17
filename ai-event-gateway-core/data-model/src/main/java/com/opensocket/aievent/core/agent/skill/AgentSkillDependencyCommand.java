package com.opensocket.aievent.core.agent.skill;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillDependencyCommand {
    private String operatorId;
    private String reason;
    private List<AgentSkillDependencyEdge> edges = List.of();
    private Map<String, Object> metadata = Map.of();
}
