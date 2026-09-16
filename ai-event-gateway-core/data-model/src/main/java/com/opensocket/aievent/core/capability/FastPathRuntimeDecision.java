package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Runtime decision chooses either Adaptive fallback or one certified semantic plan template. It never chooses a Provider/Agent/Pool/transport. */
public record FastPathRuntimeDecision(String decisionId,String tenantId,String taskRef,String problemSignature,String result,String patternId,Integer patternVersion,String certificationId,String planId,Integer planRevision,String planDecisionId,String runId,List<String> reasonCodes,OffsetDateTime decidedAt){public FastPathRuntimeDecision{reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
