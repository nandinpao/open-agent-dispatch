package com.opensocket.aievent.core.agent.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class DispatchEligibilityObservationTest {

    @Test
    void shouldRecordFirstBlockingCheckAsBoundedReasonCode() {
        RecordingHandler handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        DispatchEligibilityService service = new DispatchEligibilityService(
                mock(AgentDirectoryService.class),
                mock(AgentAssignmentService.class),
                mock(AgentGovernanceService.class),
                registry);

        AgentDispatchEligibility result = service.evaluateAgent("agent-missing");

        assertThat(result.isEligible()).isFalse();
        assertThat(handler.name).isEqualTo("dispatch.eligibility.evaluate");
        assertThat(handler.low).containsEntry("dispatch.eligibility.result", "blocked");
        assertThat(handler.low).containsEntry("dispatch.blocking.reason_code", "agent_profile_exists");
        assertThat(handler.high).containsEntry("dispatch.agent.id", "agent-missing");
    }

    private static final class RecordingHandler implements ObservationHandler<Observation.Context> {
        private String name;
        private Map<String, String> low = Map.of();
        private Map<String, String> high = Map.of();

        @Override
        public void onStop(Observation.Context context) {
            name = context.getName();
            low = toMap(context.getLowCardinalityKeyValues());
            high = toMap(context.getHighCardinalityKeyValues());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        private Map<String, String> toMap(Iterable<KeyValue> values) {
            Map<String, String> result = new LinkedHashMap<>();
            for (KeyValue value : values) result.put(value.getKey(), value.getValue());
            return result;
        }
    }
}
