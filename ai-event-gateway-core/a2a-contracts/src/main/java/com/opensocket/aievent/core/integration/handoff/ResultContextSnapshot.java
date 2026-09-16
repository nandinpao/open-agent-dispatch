package com.opensocket.aievent.core.integration.handoff;
import java.time.OffsetDateTime; import java.util.List; import java.util.Map;
public record ResultContextSnapshot(String tenantId,String resultSnapshotId,String rootTaskId,String sourceTaskId,String targetTaskId,String policyId,int snapshotVersion,String resultSummary,List<String> sharedEvidenceRefs,Map<String,Object> maskedOutput,List<String> omittedOutputReasons,String resultContentHash,String createdByAgentId,OffsetDateTime createdAt,String status,String correlationId) {}
