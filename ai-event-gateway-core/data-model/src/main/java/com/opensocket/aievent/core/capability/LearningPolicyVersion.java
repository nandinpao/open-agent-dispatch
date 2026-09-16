package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.Map;
/** Immutable learning-policy snapshot. */
public record LearningPolicyVersion(String tenantId,String policyId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt){}
