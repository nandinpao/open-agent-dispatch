package com.opensocket.aievent.core.agent.eligibility.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/** Cardinality contract for agent eligibility and governed candidate discovery. */
public enum DispatchEligibilityObservationDocumentation implements ObservationDocumentation {
    AGENT_EVALUATION("dispatch.eligibility.evaluate", "dispatch eligibility evaluate"),
    CANDIDATE_SELECTION("dispatch.eligibility.candidates", "dispatch eligibility candidates");

    private final String name;
    private final String contextualName;

    DispatchEligibilityObservationDocumentation(String name, String contextualName) {
        this.name = name;
        this.contextualName = contextualName;
    }

    @Override public String getName() { return name; }
    @Override public String getContextualName() { return contextualName; }
    @Override public KeyName[] getLowCardinalityKeyNames() { return LowCardinalityKeyNames.values(); }
    @Override public KeyName[] getHighCardinalityKeyNames() { return HighCardinalityKeyNames.values(); }

    public enum LowCardinalityKeyNames implements KeyName {
        RESULT("dispatch.eligibility.result"),
        DISPATCH_STATUS("dispatch.eligibility.status"),
        CONNECTION_STATUS("dispatch.agent.connection_status"),
        REQUIREMENT_RESOLUTION("dispatch.requirement.resolution"),
        CANDIDATE_RESULT("dispatch.candidate.result"),
        BLOCKING_REASON_CODE("dispatch.blocking.reason_code");

        private final String key;
        LowCardinalityKeyNames(String key) { this.key = key; }
        @Override public String asString() { return key; }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        TENANT_ID("tenant.id"),
        TASK_ID("dispatch.task.id"),
        AGENT_ID("dispatch.agent.id"),
        TASK_TYPE("dispatch.task.type"),
        SOURCE_SYSTEM("dispatch.source.system"),
        FLOW_ID("dispatch.flow.id"),
        RULE_ID("dispatch.rule.id"),
        REQUESTED_SKILL("dispatch.requested.skill");

        private final String key;
        HighCardinalityKeyNames(String key) { this.key = key; }
        @Override public String asString() { return key; }
    }
}
