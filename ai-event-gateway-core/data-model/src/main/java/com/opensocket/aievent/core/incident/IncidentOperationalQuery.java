package com.opensocket.aievent.core.incident;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;

/** Read-only query and operational boundary owned by the Incident module. */
public interface IncidentOperationalQuery {
    Optional<Incident> findById(String incidentId);
    List<Incident> search(IncidentQuery query);
    List<IncidentOccurrenceSummary> occurrenceSummary(String incidentId, int limit);
    Map<String, Integer> statusCounts(int limit);
    String incidentStoreMode();
    String occurrenceSummaryStoreMode();
}
