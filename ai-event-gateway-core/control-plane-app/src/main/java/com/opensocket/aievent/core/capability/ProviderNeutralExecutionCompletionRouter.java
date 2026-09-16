package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Routes provider completion by execution context; providers never decide parent continuation semantics. */
@Service
public class ProviderNeutralExecutionCompletionRouter {
    private final ProviderNeutralCapabilityExecutionCompletionService delegations;
    private final GovernedPlanExecutionService plans;
    public ProviderNeutralExecutionCompletionRouter(ProviderNeutralCapabilityExecutionCompletionService delegations,GovernedPlanExecutionService plans){this.delegations=delegations;this.plans=plans;}

    public void complete(String tenant,String contextType,String delegationId,String runId,String stepId,String taskId,
            String providerType,String providerId,String externalRef,boolean success,Map<String,Object> payload,String errorCode,String errorMessage){
        if("PLAN_STEP".equals(contextType)){
            plans.recordRuntimeCompletion(tenant,runId,stepId,new PlanRuntimeStepCompletion(externalRef,success?"SUCCEEDED":"FAILED",
                    success?providerType+" completed":errorMessage,null,List.of("provider:"+providerType,"execution:"+externalRef),
                    payload==null?Map.of():payload,null,success?null:(errorCode==null?"PROVIDER_EXECUTION_FAILED":errorCode)));
            return;
        }
        delegations.complete(tenant,delegationId,taskId,providerType,providerId,externalRef,success,payload,errorCode,errorMessage);
    }
}
