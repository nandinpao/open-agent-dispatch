package com.opensocket.aievent.core.summary;

import java.time.Duration;
import java.util.List;

import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;

public interface IncidentOccurrenceSummaryRepository {
    void recordOccurrence(Incident incident, NormalizedEvent event, String fingerprint, Duration window);
    List<IncidentOccurrenceSummary> findByIncidentId(String incidentId, int limit);
    String mode();
}
