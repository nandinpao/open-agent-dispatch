package com.opensocket.aievent.core.routing.governance.eligibility;

import com.opensocket.aievent.core.routing.governance.eligibility.AgentEligibilityShadowCheck;

public interface DispatchEligibilityShadowEvaluator {
    String code();
    AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context);
}
