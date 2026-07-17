package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillVersionPo {
    private String skillCode;
    private int version;
    private String status;
    private String definitionJson;
    private String submittedBy;
    private OffsetDateTime submittedAt;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String reviewComment;
    private String publishedBy;
    private OffsetDateTime publishedAt;
    private Integer supersedesVersion;
    private Integer rollbackOfVersion;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
