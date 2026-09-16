package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class ExternalIssueObservedStatePo {
 private String tenantId,stateId,connectionId,externalProjectId,externalIssueId,externalIssueKey,taskIssueLinkId,projectionId,
 latestObservationId,lastAppliedProviderEventId,providerChangeVersion,desiredDocumentHash,observedDocumentHash,
 normalizedObservationJson,diffJson,diffHash,providerIdentityHash,mappingSchemaHash,conflictClassification;
 private Long providerEventSequence; private OffsetDateTime lastAppliedEventAt,observedAt,updatedAt; private long version;
}
