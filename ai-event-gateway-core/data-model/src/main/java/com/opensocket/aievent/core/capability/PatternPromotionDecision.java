package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Append-only governance evidence for CANDIDATE->SHADOW->ACTIVE or retirement transitions. */
public record PatternPromotionDecision(String decisionId,String tenantId,String candidateId,String patternId,String fromStatus,String requestedStatus,String result,List<String> reasonCodes,String actorRef,OffsetDateTime decidedAt){public PatternPromotionDecision{reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
