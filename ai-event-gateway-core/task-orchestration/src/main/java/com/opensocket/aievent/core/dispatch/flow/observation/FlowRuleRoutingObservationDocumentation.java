package com.opensocket.aievent.core.dispatch.flow.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/** Cardinality contract for Flow-owned rule resolution. */
public enum FlowRuleRoutingObservationDocumentation implements ObservationDocumentation {
    FLOW_RULE_RESOLUTION {
        @Override
        public String getName() {
            return "dispatch.flow_rule.resolve";
        }

        @Override
        public String getContextualName() {
            return "dispatch flow rule resolve";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    };

    public enum LowCardinalityKeyNames implements KeyName {
        RESULT("dispatch.routing.result"),
        MATCHED("dispatch.flow_rule.matched"),
        RESOLUTION_SOURCE("dispatch.flow_rule.resolution_source"),
        EVENT_STAGE("dispatch.event.stage"),
        RULE_SCOPE("dispatch.flow_rule.scope"),
        ROUTING_PATH("dispatch.routing.path"),
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
        SOURCE_SYSTEM("dispatch.source.system"),
        TARGET_SYSTEM("dispatch.target.system"),
        EVENT_TYPE("dispatch.event.type");

        private final String key;
        HighCardinalityKeyNames(String key) { this.key = key; }
        @Override public String asString() { return key; }
    }
}
