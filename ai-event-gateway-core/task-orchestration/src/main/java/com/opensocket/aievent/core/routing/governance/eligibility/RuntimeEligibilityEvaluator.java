package com.opensocket.aievent.core.routing.governance.eligibility;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.routing.governance.eligibility.AgentEligibilityShadowCheck;
import com.opensocket.aievent.core.routing.governance.eligibility.EligibilityShadowCheckOutcome;

@Component
public class RuntimeEligibilityEvaluator implements DispatchEligibilityShadowEvaluator {
    public static final String CODE = "RUNTIME";

    @Override public String code() { return CODE; }

    @Override
    public AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context) {
        AgentSnapshot runtime = context == null ? null : context.getRuntime();
        if (runtime == null) return block("AGENT_RUNTIME_NOT_FOUND", "Agent has no runtime snapshot.");
        AgentStatus status = runtime.getStatus();
        if (runtime.isDraining() || status == AgentStatus.DRAINING) {
            return block("AGENT_RUNTIME_DRAINING", "Agent runtime is draining.");
        }
        if (runtime.isRuntimeBackoffActive()) {
            return block("AGENT_RUNTIME_BACKOFF", "Agent runtime is in failure backoff.")
                    .withDetail("backoffUntil", runtime.getRuntimeBackoffUntil())
                    .withDetail("backoffReason", runtime.getRuntimeBackoffReason());
        }
        if (status == null || status == AgentStatus.OFFLINE || status == AgentStatus.EXPIRED || status == AgentStatus.ERROR) {
            return block("AGENT_RUNTIME_NOT_READY", "Agent runtime status is not dispatch-ready.")
                    .withDetail("status", status);
        }
        if (runtime.getOwnerGatewayNodeId() == null || runtime.getOwnerGatewayNodeId().isBlank()) {
            return block("OWNER_GATEWAY_NODE_MISSING", "Agent runtime has no owner gateway node.");
        }
        if (runtime.getAgentSessionId() == null || runtime.getAgentSessionId().isBlank()) {
            return block("AGENT_SESSION_MISSING", "Agent runtime has no active session identifier.");
        }
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.PASS,
                "AGENT_RUNTIME_READY", "Agent runtime identity and transport state are ready.")
                .withDetail("status", status)
                .withDetail("ownerGatewayNodeId", runtime.getOwnerGatewayNodeId());
    }

    private AgentEligibilityShadowCheck block(String reason, String message) {
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.BLOCK, reason, message);
    }
}
