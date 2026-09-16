package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Immutable Phase 8 execution-policy snapshot. */
public record PlanExecutionPolicyVersion(String tenantId,String policyId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt) { public PlanExecutionPolicyVersion { snapshot=snapshot==null?Map.of():Map.copyOf(snapshot); } }
