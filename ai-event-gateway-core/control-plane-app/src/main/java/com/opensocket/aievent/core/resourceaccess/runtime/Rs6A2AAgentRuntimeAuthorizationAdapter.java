package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.a2a.application.port.out.A2AAgentRuntimeAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.DecisionEffect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reuses the RS4 Agent->Task principal decision for legacy compatibility. Retired directional A2A policy is not runtime authority. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class Rs6A2AAgentRuntimeAuthorizationAdapter implements A2AAgentRuntimeAuthorizationPort {
    private final AgentTaskRuntimeAuthorizationService taskRuntime;
    public Rs6A2AAgentRuntimeAuthorizationAdapter(AgentTaskRuntimeAuthorizationService taskRuntime){ this.taskRuntime=taskRuntime; }

    @Override
    public Decision authorize(String tenantId,String sourceTaskId,String agentId,String correlationId){
        AuthorizationDecision decision=taskRuntime.authorizeExecution(sourceTaskId,agentId,correlationId,"RS6_AGENT_A2A_REQUEST");
        boolean allowed=decision.effect()==DecisionEffect.ALLOW;
        return new Decision(allowed,taskRuntime.isEnforced(),allowed?"":"Agent Service Principal lacks task.execute on the source Task.");
    }
}
