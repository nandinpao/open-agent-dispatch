package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Getter @Setter @NoArgsConstructor public class A2AReconciliationEvidencePo {private String tenantId,evidenceId,caseId,eventKey,evidenceType,evidenceReference,evidenceHash,decision,reasonCode,safeSummary,actorId;private int repairAttemptNo;private OffsetDateTime occurredAt;}
