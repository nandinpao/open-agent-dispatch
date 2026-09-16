package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AParentAggregationPo {
 private String tenantId,parentTaskId,aggregationPolicy,policySnapshotHash,aggregateStatus,decisionReason,summary,computationHash,lastResultId;
 private long policyVersion,version; private int quorumCount,totalCount,resultCount,pendingCount,succeededCount,partialCount,failedCount,cancelledCount; private boolean manualDecisionRequired; private OffsetDateTime computedAt;
}
