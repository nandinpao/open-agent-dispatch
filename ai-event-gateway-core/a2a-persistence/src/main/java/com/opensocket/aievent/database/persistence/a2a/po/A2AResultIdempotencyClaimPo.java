package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AResultIdempotencyClaimPo {
 private String tenantId,idempotencyKey,requestId,resultId,acceptanceAttemptId,resultFingerprint; private OffsetDateTime claimedAt;
}
