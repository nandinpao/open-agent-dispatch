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
public class AgentCapabilityDriftItem {
    private String agentId;
    private String skillCode;
    private String driftType;
    private String severity;
    private boolean approved;
    private boolean reported;
    private boolean taxonomyKnown;
    private boolean taxonomyEnabled;
    private boolean deprecated;
    private List<String> replacementSkillCodes = List.of();
    private String suggestedAction;
    private String detail;
    private Map<String, Object> metadata = Map.of();
    private OffsetDateTime detectedAt;
}
