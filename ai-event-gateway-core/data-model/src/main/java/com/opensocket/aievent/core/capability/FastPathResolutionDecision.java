package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;import java.util.Map;
/** Fast path chooses only a governed semantic plan template. Every step still requires WHO MAY/SHOULD/HOW. */
public record FastPathResolutionDecision(String decisionId,String tenantId,String problemSignature,String result,String patternId,Integer patternVersion,Map<String,Object> planTemplate,List<String> reasonCodes,OffsetDateTime decidedAt){public FastPathResolutionDecision{planTemplate=planTemplate==null?Map.of():Map.copyOf(planTemplate);reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
