package com.opensocket.aievent.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.task.TaskRecord;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class RoutingBusinessObservationTest {

    @Test
    void shouldRecordCandidateSelectionAndFailClosedAssignmentDecision() {
        RecordingHandler handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        AgentDirectoryFacade directory = mock(AgentDirectoryFacade.class);
        when(directory.findCandidates(any())).thenReturn(List.of());
        RoutingDecisionRepository repository = new RoutingDecisionRepository() {
            @Override public RoutingDecisionRecord save(RoutingDecisionRecord decision) { return decision; }
            @Override public Optional<RoutingDecisionRecord> findById(String decisionId) { return Optional.empty(); }
            @Override public List<RoutingDecisionRecord> findByTaskId(String taskId, int limit) { return List.of(); }
            @Override public List<RoutingDecisionRecord> recent(int limit) { return List.of(); }
            @Override public String mode() { return "TEST"; }
        };
        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setZeroSpecialCaseRuntimeEnabled(false);
        properties.setEligibilityEngineMode(EligibilityEngineMode.LEGACY_ONLY.name());

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-routing-observation");
        task.setTenantId("tenant-a");
        task.setSourceSystem("MES");
        task.setEventStage("EXTERNAL");
        task.setEventType("EQUIPMENT_ALARM");
        task.setRequestedSkill("ALARM_ANALYSIS");
        task.setRoutingPolicy(RoutingPolicy.CAPABILITY_FIRST.name());

        RoutingDecisionRecord decision = new RoutingDecisionService(directory, repository, properties, registry).decide(task);

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        ObservationSnapshot candidate = handler.byName("dispatch.routing.candidates");
        ObservationSnapshot assignment = handler.byName("dispatch.assignment.decide");
        assertThat(candidate.low()).containsEntry("dispatch.candidate.result", "no_candidate");
        assertThat(candidate.low()).containsEntry("dispatch.blocking.reason_code", "no_candidate");
        assertThat(assignment.low()).containsEntry("dispatch.routing.result", "blocked");
        assertThat(assignment.low()).containsEntry("dispatch.assignment.status", "no_candidate");
        assertThat(assignment.high()).containsEntry("dispatch.task.id", "task-routing-observation");
    }

    private record ObservationSnapshot(String name, Map<String, String> low, Map<String, String> high) { }

    private static final class RecordingHandler implements ObservationHandler<Observation.Context> {
        private final List<ObservationSnapshot> snapshots = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            snapshots.add(new ObservationSnapshot(
                    context.getName(),
                    toMap(context.getLowCardinalityKeyValues()),
                    toMap(context.getHighCardinalityKeyValues())));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        ObservationSnapshot byName(String name) {
            return snapshots.stream().filter(snapshot -> name.equals(snapshot.name())).findFirst().orElseThrow();
        }

        private Map<String, String> toMap(Iterable<KeyValue> values) {
            Map<String, String> result = new LinkedHashMap<>();
            for (KeyValue value : values) result.put(value.getKey(), value.getValue());
            return result;
        }
    }
}
