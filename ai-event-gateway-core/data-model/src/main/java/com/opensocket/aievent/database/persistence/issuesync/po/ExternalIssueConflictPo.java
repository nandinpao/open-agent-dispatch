package com.opensocket.aievent.database.persistence.issuesync.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class ExternalIssueConflictPo {
 private String tenantId,conflictId,connectionId,externalProjectId,externalIssueId,taskIssueLinkId,projectionId,observationId,
 classification,resolutionPolicy,status,desiredDocumentHash,observedDocumentHash,stateDiffJson,evidenceHash,reasonCode,resolvedBy,
 resolutionReason,resolutionIdempotencyKey,resolutionRequestHash,correlationId;
 private OffsetDateTime detectedAt,decisionAt,resolvedAt; private long version;
}
