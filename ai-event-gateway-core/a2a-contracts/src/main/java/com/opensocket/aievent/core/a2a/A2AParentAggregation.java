package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AParentAggregation {
    private String tenantId;
    private String parentTaskId;
    private A2AResultAggregationPolicy aggregationPolicy;
    private long policyVersion;
    private String policySnapshotHash;
    private int quorumCount = 1;
    private String aggregateStatus;
    private String decisionReason;
    private boolean manualDecisionRequired;
    private int resultCount;
    private int totalCount;
    private int pendingCount;
    private int succeededCount;
    private int partialCount;
    private int failedCount;
    private int cancelledCount;
    private String summary;
    private String computationHash;
    private String lastResultId;
    private OffsetDateTime computedAt;
    private long version = 1L;
}
