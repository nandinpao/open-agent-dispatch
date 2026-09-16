package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AResultEvidencePo {
 private String tenantId,evidenceId,attemptId,requestId,evidenceType,evidenceReference,evidenceHash,verificationDecision,classification,resultFingerprint,reasonCode; private OffsetDateTime verifiedAt;
}
