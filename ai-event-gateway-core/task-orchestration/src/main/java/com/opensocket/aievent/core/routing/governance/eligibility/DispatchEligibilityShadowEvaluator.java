package com.opensocket.aievent.core.routing.governance.eligibility;



public interface DispatchEligibilityShadowEvaluator {
    String code();
    AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context);
}
