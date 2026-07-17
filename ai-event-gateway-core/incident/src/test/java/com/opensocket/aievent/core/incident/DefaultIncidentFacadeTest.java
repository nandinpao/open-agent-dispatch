package com.opensocket.aievent.core.incident;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummaryRepository;

class DefaultIncidentFacadeTest {
    @Test
    void repeatedObservationShouldReuseActiveIncidentAndRecordSummary() {
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        RecordingSummaryRepository summaries = new RecordingSummaryRepository();
        IncidentFacade facade = new DefaultIncidentFacade(new IncidentManager(incidents), incidents, summaries);
        OffsetDateTime first = OffsetDateTime.of(2026, 6, 11, 8, 0, 0, 0, ZoneOffset.UTC);

        Incident one = facade.observe(new IncidentObservationCommand(
                "fp-1", event("evt-1", first), first, first, 1));
        Incident two = facade.observe(new IncidentObservationCommand(
                "fp-1", event("evt-2", first.plusMinutes(1)), first, first.plusMinutes(1), 2));

        assertThat(two.getIncidentId()).isEqualTo(one.getIncidentId());
        assertThat(two.getOccurrenceCount()).isEqualTo(2);
        assertThat(incidents.findAll()).hasSize(1);
        assertThat(summaries.recordedIncidentIds).containsExactly(one.getIncidentId(), one.getIncidentId());
    }

    private static final class RecordingSummaryRepository implements IncidentOccurrenceSummaryRepository {
        private final List<String> recordedIncidentIds = new ArrayList<>();

        @Override
        public void recordOccurrence(Incident incident, NormalizedEvent event, String fingerprint, Duration window) {
            recordedIncidentIds.add(incident.getIncidentId());
        }

        @Override
        public List<IncidentOccurrenceSummary> findByIncidentId(String incidentId, int limit) {
            return List.of();
        }

        @Override
        public String mode() {
            return "TEST";
        }
    }

    private NormalizedEvent event(String eventId, OffsetDateTime observedAt) {
        return new NormalizedEvent(
                eventId, "TENANT-A", "MES", "EXTERNAL", "MES", "NO_TARGET_SYSTEM", "TNN", "FAB-01", "EQUIPMENT", "EQ-1",
                "ALARM", "TEMP_HIGH", "NO_REQUESTED_SKILL", "NO_HANDOFF_MODE", "NO_CORRELATION_ID", "NO_PARENT_TASK_ID", EventSeverity.HIGH, "temperature high", observedAt, Map.of());
    }
}
