package com.opensocket.aievent.core.incident;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.event.NormalizedEvent;

@Service
public class IncidentManager {
    private final IncidentRepository incidentRepository;
    private final IncidentModuleProperties properties;

    public IncidentManager(IncidentRepository incidentRepository) {
        this(incidentRepository, new IncidentModuleProperties());
    }

    @Autowired
    public IncidentManager(IncidentRepository incidentRepository, IncidentModuleProperties properties) {
        this.incidentRepository = incidentRepository;
        this.properties = properties == null ? new IncidentModuleProperties() : properties;
    }

    public Incident getOrCreate(IncidentObservationCommand command) {
        return incidentRepository.findActiveByFingerprint(command.fingerprint())
                .map(existing -> updateExisting(existing, command))
                .orElseGet(() -> reopenRecentOrCreateNew(command));
    }

    private Incident reopenRecentOrCreateNew(IncidentObservationCommand command) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return incidentRepository.findLatestByFingerprint(command.fingerprint())
                .filter(existing -> shouldReopen(existing, now))
                .map(existing -> reopenExisting(existing, command, now))
                .orElseGet(() -> createNew(command));
    }

    private boolean shouldReopen(Incident incident, OffsetDateTime now) {
        if (incident == null || incident.getStatus() != IncidentStatus.RESOLVED) {
            return false;
        }
        if (properties.getReopenPolicy() != IncidentModuleProperties.ReopenPolicy.REOPEN_RECENT) {
            return false;
        }
        OffsetDateTime base = incident.getResolvedAt() == null ? incident.getLastSeenAt() : incident.getResolvedAt();
        return base != null && !base.isBefore(now.minus(properties.getReopenWindow()));
    }

    private Incident createNew(IncidentObservationCommand command) {
        NormalizedEvent event = command.event();
        Incident incident = new Incident();
        incident.setIncidentId("inc-" + UUID.randomUUID());
        incident.setFingerprint(command.fingerprint());
        incident.setTenantId(event.tenantId());
        incident.setSourceSystem(event.sourceSystem());
        incident.setSiteId(event.siteId());
        incident.setPlantId(event.plantId());
        incident.setObjectType(event.objectType());
        incident.setObjectId(event.objectId());
        incident.setEventType(event.eventType());
        incident.setErrorCode(event.errorCode());
        incident.setSeverity(event.severity());
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setFirstSeenAt(command.firstSeenAt());
        incident.setLastSeenAt(command.lastSeenAt());
        incident.setOccurrenceCount(command.occurrenceCount());
        incident.setLastMessage(event.normalizedMessage());
        incident.setLifecycleReason("Created from event " + event.eventId());
        Incident saved = incidentRepository.saveNewOrGetActive(incident);
        if (!saved.getIncidentId().equals(incident.getIncidentId())) {
            return updateExisting(saved, command);
        }
        return saved;
    }

    private Incident reopenExisting(Incident incident,
                                    IncidentObservationCommand command,
                                    OffsetDateTime now) {
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setResolvedAt(null);
        incident.setReopenedAt(now);
        incident.setReopenCount(incident.getReopenCount() + 1);
        incident.setLifecycleReason("Reopened by recurring event " + command.event().eventId());
        return updateExisting(incident, command);
    }

    private Incident updateExisting(Incident incident, IncidentObservationCommand command) {
        NormalizedEvent event = command.event();
        if (event.severity().higherThan(incident.getSeverity())) {
            incident.setSeverity(event.severity());
            incident.setStatus(IncidentStatus.ESCALATED);
            incident.setLifecycleReason("Escalated by event " + event.eventId());
        }
        incident.setLastSeenAt(command.lastSeenAt());
        incident.setOccurrenceCount(command.occurrenceCount());
        incident.setLastMessage(event.normalizedMessage());
        return incidentRepository.save(incident);
    }
}
