package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationConflictPo {
 private String tenantId;
 private String conflictId;
 private String taskId;
 private String taskIssueLinkId;
 private String inboxId;
 private String conflictType;
 private String taskStatus;
 private String externalIssueStatus;
 private String detailJson;
 private String status;
 private OffsetDateTime detectedAt;
 private OffsetDateTime resolvedAt;
 private String resolvedBy;
 private String resolutionAction;
 private String resolutionReason;
 private String correlationId;
}
