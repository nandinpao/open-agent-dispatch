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
public class AgentSkillDeprecationMigrationPlan {
    private String skillCode;
    private String status;
    private boolean deprecated;
    private boolean canDeprecate;
    private List<String> replacementSkillCodes = List.of();
    private List<String> impactedAgentIds = List.of();
    private List<AgentSkillImpactAgent> impactedAgents = List.of();
    private List<AgentCapabilityDriftItem> driftItems = List.of();
    private List<String> migrationSteps = List.of();
    private List<String> blockingReasons = List.of();
    private String severity;
    private Map<String, Object> metadata = Map.of();
    private OffsetDateTime generatedAt;
}
