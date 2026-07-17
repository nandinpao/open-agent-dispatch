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
public class AgentSkillDeprecationPlan {
    private String skillCode;
    private String status;
    private List<String> replacementSkillCodes = List.of();
    private OffsetDateTime migrationDeadline;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private Map<String, Object> metadata = Map.of();
}
