package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
@Getter @Setter @NoArgsConstructor public class A2ACutoverEvidencePo {
 private String evidenceId,scopeId,fromStage,toStage,evidenceType,evidenceReference,evidenceHash,actorId;private OffsetDateTime occurredAt;
}
