package com.opensocket.aievent.core.agent.skill;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillRemediationAction {
    private String actionId;
    private String agentId;
    private String skillCode;
    private String actionType;
    private String severity;
    private boolean executable;
    private String targetSkillCode;
    private List<String> prerequisites = List.of();
    private String reason;
    private Map<String, Object> commandHint = Map.of();
    private Map<String, Object> metadata = Map.of();
}
