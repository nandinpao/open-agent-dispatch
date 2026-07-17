package com.opensocket.aievent.core.routing.governance.eligibility;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.governance.eligibility.AgentEligibilityShadowCheck;
import com.opensocket.aievent.core.routing.governance.eligibility.EligibilityShadowCheckOutcome;

@Component
public class CapacityEligibilityEvaluator implements DispatchEligibilityShadowEvaluator {
    public static final String CODE = "CAPACITY";

    @Override public String code() { return CODE; }

    @Override
    public AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context) {
        AgentSnapshot runtime = context == null ? null : context.getRuntime();
        if (runtime == null) return block("AGENT_RUNTIME_NOT_FOUND", "Capacity cannot be evaluated without a runtime snapshot.");
        boolean available = runtime.getAvailableSlots() > 0
                || runtime.getEffectiveTaskCount() < Math.max(1, runtime.getMaxConcurrentTasks());
        if (!available) {
            return block("AGENT_CAPACITY_EXHAUSTED", "Agent has no available dispatch capacity.")
                    .withDetail("availableSlots", runtime.getAvailableSlots())
                    .withDetail("effectiveTaskCount", runtime.getEffectiveTaskCount())
                    .withDetail("maxConcurrentTasks", runtime.getMaxConcurrentTasks());
        }
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.PASS,
                "AGENT_CAPACITY_AVAILABLE", "Agent has available dispatch capacity.")
                .withDetail("availableSlots", runtime.getAvailableSlots())
                .withDetail("effectiveTaskCount", runtime.getEffectiveTaskCount())
                .withDetail("maxConcurrentTasks", runtime.getMaxConcurrentTasks());
    }

    private AgentEligibilityShadowCheck block(String reason, String message) {
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.BLOCK, reason, message);
    }
}
