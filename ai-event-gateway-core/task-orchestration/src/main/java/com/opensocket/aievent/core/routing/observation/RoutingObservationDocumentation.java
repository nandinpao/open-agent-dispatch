package com.opensocket.aievent.core.routing.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/** Cardinality contract for candidate scoring and the final assignment decision. */
public enum RoutingObservationDocumentation implements ObservationDocumentation {
    CANDIDATE_SELECTION("dispatch.routing.candidates", "dispatch routing candidates"),
    ASSIGNMENT_DECISION("dispatch.assignment.decide", "dispatch assignment decide");

    private final String name;
    private final String contextualName;

    RoutingObservationDocumentation(String name, String contextualName) {
        this.name = name;
        this.contextualName = contextualName;
    }

    @Override public String getName() { return name; }
    @Override public String getContextualName() { return contextualName; }
    @Override public KeyName[] getLowCardinalityKeyNames() { return LowCardinalityKeyNames.values(); }
    @Override public KeyName[] getHighCardinalityKeyNames() { return HighCardinalityKeyNames.values(); }

    public enum LowCardinalityKeyNames implements KeyName {
        RESULT("dispatch.routing.result"),
        ROUTING_POLICY("dispatch.routing.policy"),
        ELIGIBILITY_MODE("dispatch.eligibility.mode"),
        CANDIDATE_RESULT("dispatch.candidate.result"),
        ASSIGNMENT_STATUS("dispatch.assignment.status"),
        BLOCKING_REASON_CODE("dispatch.blocking.reason_code");

        private final String key;
        LowCardinalityKeyNames(String key) { this.key = key; }
        @Override public String asString() { return key; }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        TENANT_ID("tenant.id"),
        TASK_ID("dispatch.task.id"),
        FLOW_ID("dispatch.flow.id"),
        RULE_ID("dispatch.rule.id"),
        REQUESTED_SKILL("dispatch.requested.skill"),
        DECISION_ID("dispatch.routing.decision.id"),
        SELECTED_AGENT_ID("dispatch.agent.id");

        private final String key;
        HighCardinalityKeyNames(String key) { this.key = key; }
        @Override public String asString() { return key; }
    }
}
