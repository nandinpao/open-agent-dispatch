package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.Map;
/** Append-only runtime policy snapshot. */
public record FastPathRuntimePolicyVersion(String tenantId,String policyId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt){public FastPathRuntimePolicyVersion{snapshot=snapshot==null?Map.of():Map.copyOf(snapshot);}}
