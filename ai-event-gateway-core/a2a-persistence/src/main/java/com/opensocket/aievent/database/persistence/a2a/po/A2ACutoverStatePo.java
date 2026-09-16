package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
@Getter @Setter @NoArgsConstructor public class A2ACutoverStatePo {
 private String scopeId,stage,migrationEvidenceReference,runtimeGateRunId,releaseEvidenceReference,updatedBy;
 private boolean shadowReadEnabled,legacyWriteEnabled,issueTrackingEnabled;private long shadowMismatchCount,version;
 private OffsetDateTime rollbackDeadline,updatedAt;
}
