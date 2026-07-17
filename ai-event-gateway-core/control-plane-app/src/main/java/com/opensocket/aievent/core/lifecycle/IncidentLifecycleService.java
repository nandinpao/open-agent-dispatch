package com.opensocket.aievent.core.lifecycle;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.observability.CoreMetricsService;

@Service
public class IncidentLifecycleService {
    private final IncidentFacade incidentFacade;
    private final LifecycleProperties properties;

    @Autowired(required = false)
    private CoreMetricsService metrics;

    public IncidentLifecycleService(IncidentFacade incidentFacade, LifecycleProperties properties) {
        this.incidentFacade = incidentFacade;
        this.properties = properties;
    }

    public Incident resolve(String incidentId, String reason) {
        return incidentFacade.resolve(incidentId, firstNonBlank(reason, "Manually resolved"), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public Incident reopen(String incidentId, String reason) {
        return incidentFacade.reopen(incidentId, firstNonBlank(reason, "Manually reopened"), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public Incident suppress(String incidentId, String reason) {
        return incidentFacade.suppress(incidentId, firstNonBlank(reason, "Manually suppressed"), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public LifecycleScanResult autoResolveStaleIncidents() {
        if (!properties.getIncident().isAutoResolveEnabled()) {
            return LifecycleScanResult.empty("Incident auto-resolve is disabled");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minus(properties.getIncident().getInactiveThreshold());
        LifecycleScanResult result = incidentFacade.autoResolveStale(
                cutoff,
                properties.getIncident().getMaxBatchSize(),
                "Auto-resolved after inactive threshold " + properties.getIncident().getInactiveThreshold(),
                now);
        record("incident", result);
        return result;
    }

    private void record(String target, LifecycleScanResult result) {
        if (metrics != null) metrics.recordLifecycleScan(target, result);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
