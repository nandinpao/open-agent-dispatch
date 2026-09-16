package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class ExternalIssueConflictEventPo {
 private String tenantId,eventId,conflictId,eventType,actorId,reasonCode,metadataJson,previousEventHash,eventHash,correlationId;
 private OffsetDateTime occurredAt;
}
