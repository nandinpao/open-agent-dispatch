package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.decision.DecisionEngine;
import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.decision.EventDecisionRepository;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.decision.InMemoryEventDecisionRepository;
import com.opensocket.aievent.core.dedup.DedupStateStore;
import com.opensocket.aievent.core.dedup.InMemoryDedupStateStore;
import com.opensocket.aievent.core.dedup.snapshot.NoopDedupStateSnapshotRepository;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.fingerprint.FingerprintGenerator;
import com.opensocket.aievent.core.incident.InMemoryIncidentRepository;
import com.opensocket.aievent.core.incident.IncidentManager;
import com.opensocket.aievent.core.incident.DefaultIncidentFacade;
import com.opensocket.aievent.core.normalize.EventNormalizer;
import com.opensocket.aievent.core.processing.DefaultEventProcessingFacade;
import com.opensocket.aievent.core.processing.EventProcessingProperties;
import com.opensocket.aievent.core.summary.InMemoryIncidentOccurrenceSummaryRepository;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskDecisionService;
import com.opensocket.aievent.core.task.TaskOrchestrationProperties;

class DecisionEngineTest {
    @Test
    void duplicateEventShouldAggregateOccurrenceWithoutCreatingTaskMcpOrIssue() {
        TaskOrchestrationProperties properties = new TaskOrchestrationProperties();
        properties.setTaskCreationEnabled(true);
        DedupStateStore dedup = new InMemoryDedupStateStore();
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        IncidentManager incidentManager = new IncidentManager(incidents);
        EventDecisionRepository decisions = new InMemoryEventDecisionRepository();
        InMemoryIncidentOccurrenceSummaryRepository summaries = new InMemoryIncidentOccurrenceSummaryRepository();
        DefaultIncidentFacade incidentFacade = new DefaultIncidentFacade(incidentManager, incidents, summaries);
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        TaskDecisionService taskDecisionService = new TaskDecisionService(
                taskRepository,
                incidentFacade,
                properties,
                null);
        EventProcessingProperties eventProperties = new EventProcessingProperties();
        DecisionEngine engine = new DecisionEngine(
                new DefaultEventProcessingFacade(
                        new EventNormalizer(),
                        new FingerprintGenerator(),
                        dedup,
                        new NoopDedupStateSnapshotRepository(),
                        incidentFacade,
                        eventProperties),
                decisions,
                new DefaultTaskOrchestrationFacade(taskDecisionService, null, taskRepository));

        EventIntakeDecisionResponse first = engine.ingest(request());
        EventIntakeDecisionResponse second = engine.ingest(request());

        assertThat(first.decisionType()).isEqualTo(DecisionType.INCIDENT_CREATED);
        assertThat(second.decisionType()).isEqualTo(DecisionType.DUPLICATE_AGGREGATED);
        assertThat(second.duplicate()).isTrue();
        assertThat(second.occurrenceCount()).isEqualTo(2);
        assertThat(second.taskCreated()).isFalse();
        assertThat(second.taskSuppressed()).isTrue();
        assertThat(second.actions()).contains(DecisionAction.EVENT_NORMALIZED,
                DecisionAction.FINGERPRINT_GENERATED,
                DecisionAction.DEDUP_EVALUATED,
                DecisionAction.INCIDENT_OBSERVED,
                DecisionAction.SUPPRESS_DUPLICATE_EVENT,
                DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK);
        assertThat(second.mcpCalled()).isFalse();
        assertThat(second.issueCreated()).isFalse();
        assertThat(first.incidentId()).isEqualTo(second.incidentId());
    }

    private EventIntakeRequest request() {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("MES");
        request.setSiteId("TNN");
        request.setPlantId("TNN-FAB-01");
        request.setObjectType("EQUIPMENT");
        request.setObjectId("EQP-1001");
        request.setEventType("EQUIPMENT_ALARM");
        request.setErrorCode("TEMP_HIGH");
        request.setSeverity("HIGH");
        request.setMessage("Chamber temperature over threshold");
        return request;
    }
}
