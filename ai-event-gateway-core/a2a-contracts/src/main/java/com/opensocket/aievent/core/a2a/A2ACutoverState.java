package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2ACutoverState {
    private String scopeId = "INSTANCE";
    private A2ACutoverStage stage = A2ACutoverStage.EXPAND;
    private boolean shadowReadEnabled;
    private boolean legacyWriteEnabled = true;
    private boolean issueTrackingEnabled = true;
    private long shadowMismatchCount;
    private String migrationEvidenceReference;
    private String runtimeGateRunId;
    private String releaseEvidenceReference;
    private OffsetDateTime rollbackDeadline;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private long version = 1L;
}
