package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2ACutoverEvidence {
    private String evidenceId;
    private String scopeId;
    private A2ACutoverStage fromStage;
    private A2ACutoverStage toStage;
    private String evidenceType;
    private String evidenceReference;
    private String evidenceHash;
    private String actorId;
    private OffsetDateTime occurredAt;
}
