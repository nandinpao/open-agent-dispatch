package com.opensocket.aievent.core.lifecycle;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledIncidentLifecycle {
    private final IncidentLifecycleService service;

    public ScheduledIncidentLifecycle(IncidentLifecycleService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${core.lifecycle.incident.scan-interval-ms:60000}")
    public void autoResolveStaleIncidents() {
        service.autoResolveStaleIncidents();
    }
}
