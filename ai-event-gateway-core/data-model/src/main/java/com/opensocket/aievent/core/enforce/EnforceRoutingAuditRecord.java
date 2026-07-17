package com.opensocket.aievent.core.enforce;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record EnforceRoutingAuditRecord(
        String decisionId,
        String taskId,
        String agentId,
        String policyCode,
        String blockingCode,
        String eligibilityEngineMode,
        boolean eligibilityV2Applied,
        boolean eligibilityV2CandidateEligible,
        Integer eligibilityV2Score,
        List<String> eligibilityV2BlockingReasons,
        Map<String, Object> eligibilityV2ScoreBreakdown,
        OffsetDateTime createdAt) {
}
