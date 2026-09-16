package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class ExternalIssueObservationPo {
 private String tenantId,observationId,inboxId,connectionId,providerType,providerEventId,providerChangeVersion,
 externalProjectId,externalIssueId,externalIssueKey,externalIssueStatus,rawObservedDocumentJson,normalizedObservationJson,
 observedDocumentHash,normalizationProfileVersion,providerIdentityHash,mappingSchemaHash,correlationId;
 private Long providerEventSequence; private OffsetDateTime providerObservedAt,createdAt;
}
