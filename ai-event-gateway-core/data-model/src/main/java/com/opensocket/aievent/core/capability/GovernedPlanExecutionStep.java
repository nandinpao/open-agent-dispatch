package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Runtime view of one frozen Plan Step. A0-R5 carries its immutable Plan-time authorization boundary. */
public record GovernedPlanExecutionStep(
        String tenantId,String runId,String stepId,CapabilityRequirement requiredCapability,List<String> dependsOn,
        boolean required,String state,int attemptCount,String authorizationDecisionId,String routingDecisionId,
        String adapterResolutionId,String childTaskRef,OffsetDateTime deadlineAt,OffsetDateTime updatedAt,
        String bindingAuthorizationEnvelopeId,String sideEffect,String writeSemantics,String compensationBindingId,
        int maxBindingFallback) {
    public GovernedPlanExecutionStep { dependsOn=dependsOn==null?List.of():List.copyOf(dependsOn); }
    public GovernedPlanExecutionStep(String tenantId,String runId,String stepId,CapabilityRequirement requiredCapability,List<String> dependsOn,
            boolean required,String state,int attemptCount,String authorizationDecisionId,String routingDecisionId,String adapterResolutionId,
            String childTaskRef,OffsetDateTime deadlineAt,OffsetDateTime updatedAt){
        this(tenantId,runId,stepId,requiredCapability,dependsOn,required,state,attemptCount,authorizationDecisionId,routingDecisionId,
             adapterResolutionId,childTaskRef,deadlineAt,updatedAt,null,"READ",null,null,0);
    }
}
