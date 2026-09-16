package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Phase 9 preparation evidence. READY means WHAT is prepared; WHO CAN/MAY/SHOULD/HOW must still run. */
public record AggregationPreparationDecision(String decisionId,String tenantId,String aggregationId,int aggregationVersion,String runId,String result,CapabilityRequirement aggregationRequirement,List<String> inputArtifactIds,List<String> reasonCodes,boolean requiresHumanReview,OffsetDateTime createdAt){public AggregationPreparationDecision{inputArtifactIds=inputArtifactIds==null?List.of():List.copyOf(inputArtifactIds);reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
