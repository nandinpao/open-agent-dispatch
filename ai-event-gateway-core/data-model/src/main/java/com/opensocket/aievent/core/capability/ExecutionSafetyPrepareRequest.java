package com.opensocket.aievent.core.capability;

import java.util.Map;

/** Creates Assignment + Lease + DispatchIntent atomically from a fresh R6 routing evaluation. */
public record ExecutionSafetyPrepareRequest(
        Integer planRevision,String stepId,String routingProfileId,String ownerNodeId,Integer leaseTtlSeconds,
        String humanApprovalRef,Map<String,Object> input) {
    public ExecutionSafetyPrepareRequest { input=input==null?Map.of():Map.copyOf(input); }
}
