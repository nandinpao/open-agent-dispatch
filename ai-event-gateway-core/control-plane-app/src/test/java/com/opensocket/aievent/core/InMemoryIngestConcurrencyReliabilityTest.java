package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.decision.DecisionEngine;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.decision.InMemoryEventDecisionRepository;
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

class InMemoryIngestConcurrencyReliabilityTest {
    @Test
    void concurrentDuplicateIngestShouldStayOnOneIncident() throws Exception {
        TaskOrchestrationProperties properties = new TaskOrchestrationProperties();
        properties.setTaskCreationEnabled(false);

        InMemoryDedupStateStore dedup = new InMemoryDedupStateStore();
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        InMemoryIncidentOccurrenceSummaryRepository summaries = new InMemoryIncidentOccurrenceSummaryRepository();
        EventProcessingProperties eventProperties = new EventProcessingProperties();
        DefaultIncidentFacade incidentFacade = new DefaultIncidentFacade(new IncidentManager(incidents), incidents, summaries);
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        TaskDecisionService taskDecisionService = new TaskDecisionService(taskRepository, incidentFacade, properties, null);
        DecisionEngine engine = new DecisionEngine(
                new DefaultEventProcessingFacade(
                        new EventNormalizer(),
                        new FingerprintGenerator(),
                        dedup,
                        new NoopDedupStateSnapshotRepository(),
                        incidentFacade,
                        eventProperties),
                new InMemoryEventDecisionRepository(),
                new DefaultTaskOrchestrationFacade(taskDecisionService, null, taskRepository));

        int workers = 16;
        int calls = 200;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EventIntakeDecisionResponse>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return engine.ingest(request());
            }));
        }

        start.countDown();
        List<EventIntakeDecisionResponse> responses = new ArrayList<>();
        for (Future<EventIntakeDecisionResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        Set<String> incidentIds = responses.stream()
                .map(EventIntakeDecisionResponse::incidentId)
                .collect(java.util.stream.Collectors.toSet());
        long maxOccurrence = responses.stream()
                .mapToLong(EventIntakeDecisionResponse::occurrenceCount)
                .max()
                .orElse(0L);

        assertThat(incidentIds).hasSize(1);
        assertThat(incidents.findAll()).hasSize(1);
        assertThat(maxOccurrence).isEqualTo(calls);
        assertThat(dedup.find(responses.get(0).fingerprint())).isPresent()
                .get()
                .satisfies(state -> assertThat(state.getOccurrenceCount()).isEqualTo((long) calls));
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
