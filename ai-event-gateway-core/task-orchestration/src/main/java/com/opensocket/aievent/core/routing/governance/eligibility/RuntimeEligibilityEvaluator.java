package com.opensocket.aievent.core.routing.governance.eligibility;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityAssessment;

/**
 * Compatibility projection for the eligibility evidence pipeline. Runtime
 * decisions are delegated to the canonical evaluator used by dispatch.
 */
@Component("runtimeEligibilityShadowEvaluator")
public class RuntimeEligibilityEvaluator implements DispatchEligibilityShadowEvaluator {
    public static final String CODE = "RUNTIME";

    private final com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator canonicalEvaluator;

    public RuntimeEligibilityEvaluator(
            com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator canonicalEvaluator) {
        this.canonicalEvaluator = canonicalEvaluator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context) {
        RuntimeEligibilityAssessment assessment = canonicalEvaluator.assess(context == null ? null : context.getRuntime());
        AgentEligibilityShadowCheck check = AgentEligibilityShadowCheck.of(
                CODE,
                assessment.eligible() ? EligibilityShadowCheckOutcome.PASS : EligibilityShadowCheckOutcome.BLOCK,
                assessment.reasonCode(),
                assessment.message());
        for (Map.Entry<String, Object> detail : assessment.details().entrySet()) {
            check.withDetail(detail.getKey(), detail.getValue());
        }
        return check;
    }
}
