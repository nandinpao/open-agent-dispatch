package com.opensocket.aievent.core.incident;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;

/** Public application boundary owned by the Incident module. */
public interface IncidentFacade {
    Incident observe(IncidentObservationCommand command);
    Incident linkTaskIfAbsent(String incidentId, String taskId);
    default Incident linkIssueIfAbsent(String incidentId, String issueId) { throw new UnsupportedOperationException("linkIssueIfAbsent is not implemented"); }
    Optional<Incident> findById(String incidentId);

    default Incident resolve(String incidentId, String reason, OffsetDateTime now) { throw new UnsupportedOperationException("resolve is not implemented"); }
    default Incident reopen(String incidentId, String reason, OffsetDateTime now) { throw new UnsupportedOperationException("reopen is not implemented"); }
    default Incident suppress(String incidentId, String reason, OffsetDateTime now) { throw new UnsupportedOperationException("suppress is not implemented"); }
    default LifecycleScanResult autoResolveStale(OffsetDateTime cutoff, int limit, String reason, OffsetDateTime now) { return LifecycleScanResult.empty("auto resolve is not implemented"); }
}
