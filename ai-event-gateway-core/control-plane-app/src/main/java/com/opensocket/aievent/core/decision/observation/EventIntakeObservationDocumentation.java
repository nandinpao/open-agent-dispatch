package com.opensocket.aievent.core.decision.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/** Cardinality contract for event intake business observations. */
public enum EventIntakeObservationDocumentation implements ObservationDocumentation {
    EVENT_INTAKE {
        @Override
        public String getName() {
            return "dispatch.event.intake";
        }

        @Override
        public String getContextualName() {
            return "dispatch event intake";
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
        EVENT_STAGE("dispatch.event.stage"),
        RESULT("dispatch.result"),
        DECISION_TYPE("dispatch.decision.type"),
        DUPLICATE("dispatch.duplicate"),
        TASK_CREATED("dispatch.task.created"),
        ASSIGNMENT_CREATED("dispatch.assignment.created");

        private final String key;

        LowCardinalityKeyNames(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        TENANT_ID("tenant.id"),
        CORRELATION_ID("dispatch.correlation.id"),
        SOURCE_SYSTEM("dispatch.source.system"),
        EVENT_TYPE("dispatch.event.type"),
        REQUESTED_SKILL("dispatch.requested.skill"),
        PARENT_TASK_ID("dispatch.parent.task.id"),
        EVENT_ID("dispatch.event.id"),
        TASK_ID("dispatch.task.id"),
        AGENT_ID("dispatch.agent.id"),
        ROUTING_DECISION_ID("dispatch.routing.decision.id");

        private final String key;

        HighCardinalityKeyNames(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }
    }
}
