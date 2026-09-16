package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IssueRelationshipPo {
 private String tenantId;
 private String relationshipId;
 private String fromTaskIssueLinkId;
 private String toTaskIssueLinkId;
 private String relationshipType;
 private String providerRelationId;
 private String syncStatus;
 private String payloadHash;
 private String idempotencyKey;
 private String lastErrorCode;
 private String lastErrorMessage;
 private long resourceVersion;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
}
