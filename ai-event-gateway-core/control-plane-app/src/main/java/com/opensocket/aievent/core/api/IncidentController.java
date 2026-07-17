package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.*;
import com.opensocket.aievent.core.lifecycle.IncidentLifecycleService;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentOperationalQuery queryService;
    private final IncidentLifecycleService lifecycleService;

    public IncidentController(IncidentOperationalQuery queryService, IncidentLifecycleService lifecycleService) {
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public List<Incident> incidents(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) String sourceSystem,
                                    @RequestParam(required = false) String siteId,
                                    @RequestParam(required = false) String plantId,
                                    @RequestParam(required = false) String objectType,
                                    @RequestParam(required = false) String objectId,
                                    @RequestParam(required = false) String eventType,
                                    @RequestParam(required = false) String errorCode,
                                    @RequestParam(required = false) String severity,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "100") int limit) {
        IncidentQuery query = new IncidentQuery();
        query.setTenantId(tenantId); query.setSourceSystem(sourceSystem); query.setSiteId(siteId); query.setPlantId(plantId);
        query.setObjectType(objectType); query.setObjectId(objectId); query.setEventType(eventType); query.setErrorCode(errorCode);
        if (severity != null && !severity.isBlank()) query.setSeverity(EventSeverity.parse(severity));
        if (status != null && !status.isBlank()) query.setStatus(IncidentStatus.valueOf(status.trim().toUpperCase()));
        query.setLimit(limit);
        return queryService.search(query);
    }

    @GetMapping("/{incidentId}")
    public Incident incident(@PathVariable String incidentId) {
        return queryService.findById(incidentId).orElseThrow(() -> new IllegalArgumentException("incident not found: " + incidentId));
    }

    @GetMapping("/{incidentId}/occurrence-summary")
    public List<IncidentOccurrenceSummary> occurrenceSummary(@PathVariable String incidentId,
                                                             @RequestParam(defaultValue = "100") int limit) {
        return queryService.occurrenceSummary(incidentId, limit);
    }

    @PostMapping("/{incidentId}/resolve") public Incident resolve(@PathVariable String incidentId, @RequestBody(required=false) LifecycleRequest r) { return lifecycleService.resolve(incidentId, r == null ? null : r.reason()); }
    @PostMapping("/{incidentId}/reopen") public Incident reopen(@PathVariable String incidentId, @RequestBody(required=false) LifecycleRequest r) { return lifecycleService.reopen(incidentId, r == null ? null : r.reason()); }
    @PostMapping("/{incidentId}/suppress") public Incident suppress(@PathVariable String incidentId, @RequestBody(required=false) LifecycleRequest r) { return lifecycleService.suppress(incidentId, r == null ? null : r.reason()); }
    @PostMapping("/lifecycle/auto-resolve") public LifecycleScanResult autoResolve() { return lifecycleService.autoResolveStaleIncidents(); }
    public record LifecycleRequest(String reason) {}
}
