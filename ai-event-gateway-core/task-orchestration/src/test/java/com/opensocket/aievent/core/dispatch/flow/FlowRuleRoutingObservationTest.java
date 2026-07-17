package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.TaskRecord;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class FlowRuleRoutingObservationTest {

    @Test
    void shouldRecordFailClosedFlowRuleResolutionWithBoundedBlockingCode() {
        RecordingHandler handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-observation-1");
        task.setTenantId("tenant-a");
        task.setSourceSystem("MES");
        task.setEventStage("EXTERNAL");
        task.setEventType("EQUIPMENT_ALARM");
        task.setRequestedSkill("ALARM_ANALYSIS");

        FlowRuleRoutingPlan plan = new FlowRuleRoutingService(query -> Optional.empty(), registry).resolve(task);

        assertThat(plan.isMatched()).isFalse();
        assertThat(handler.name).isEqualTo("dispatch.flow_rule.resolve");
        assertThat(handler.low).containsEntry("dispatch.routing.result", "not_matched");
        assertThat(handler.low).containsEntry("dispatch.flow_rule.matched", "false");
        assertThat(handler.low).containsEntry("dispatch.blocking.reason_code", "no_active_flow_rule");
        assertThat(handler.high).containsEntry("dispatch.task.id", "task-observation-1");
        assertThat(handler.high).containsEntry("dispatch.requested.skill", "ALARM_ANALYSIS");
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
