package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class WebhookReplayEvidencePo {
 private String tenantId,evidenceId,inboxId,eventType,decision,reasonCode,evidenceHash,actorId,correlationId; private OffsetDateTime occurredAt;
}
