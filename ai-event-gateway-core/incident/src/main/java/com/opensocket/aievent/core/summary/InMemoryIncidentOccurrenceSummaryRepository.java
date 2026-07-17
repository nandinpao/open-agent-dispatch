package com.opensocket.aievent.core.summary;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "incident.summary", name = "store", havingValue = "MEMORY")
public class InMemoryIncidentOccurrenceSummaryRepository implements IncidentOccurrenceSummaryRepository {
    @Override
    public void recordOccurrence(Incident incident, NormalizedEvent event, String fingerprint, Duration window) {
        // P2 keeps MEMORY summary as no-op. PostgreSQL mode stores queryable time-window summaries.
    }

    @Override
    public List<IncidentOccurrenceSummary> findByIncidentId(String incidentId, int limit) {
        return List.of();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }
}
