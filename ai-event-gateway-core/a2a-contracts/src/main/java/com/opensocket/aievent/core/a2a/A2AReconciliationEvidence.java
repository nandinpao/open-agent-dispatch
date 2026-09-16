package com.opensocket.aievent.core.a2a;
import java.time.OffsetDateTime;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Getter @Setter @NoArgsConstructor
public class A2AReconciliationEvidence {
    private String tenantId;
    private String evidenceId;
    private String caseId;
    private int repairAttemptNo;
    private String eventKey;
    private String evidenceType;
    private String evidenceReference;
    private String evidenceHash;
    private String decision;
    private String reasonCode;
    private String safeSummary;
    private String actorId;
    private OffsetDateTime occurredAt;
}
