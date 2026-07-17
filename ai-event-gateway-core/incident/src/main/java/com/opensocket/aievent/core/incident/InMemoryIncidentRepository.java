package com.opensocket.aievent.core.incident;

import java.util.Comparator;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "incident", name = "store", havingValue = "MEMORY")
public class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();
    private final Map<String, String> activeByFingerprint = new ConcurrentHashMap<>();

    @Override
    public synchronized Incident save(Incident incident) {
        incidents.put(incident.getIncidentId(), incident);
        if (incident.getStatus() == IncidentStatus.ACTIVE || incident.getStatus() == IncidentStatus.ESCALATED) {
            activeByFingerprint.put(incident.getFingerprint(), incident.getIncidentId());
        } else {
            activeByFingerprint.remove(incident.getFingerprint(), incident.getIncidentId());
        }
        return incident;
    }

    @Override
    public synchronized Incident saveNewOrGetActive(Incident incident) {
        Optional<Incident> existing = findActiveByFingerprint(incident.getFingerprint());
        if (existing.isPresent()) {
            return existing.get();
        }
        return save(incident);
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
        return Optional.ofNullable(incidents.get(incidentId));
    }

    @Override
    public Optional<Incident> findActiveByFingerprint(String fingerprint) {
        String incidentId = activeByFingerprint.get(fingerprint);
        return incidentId == null ? Optional.empty() : findById(incidentId);
    }


    @Override
    public Optional<Incident> findLatestByFingerprint(String fingerprint) {
        return incidents.values().stream()
                .filter(i -> fingerprint != null && fingerprint.equals(i.getFingerprint()))
                .max(Comparator.comparing(Incident::getLastSeenAt));
    }

    @Override
    public List<Incident> findActiveLastSeenBefore(OffsetDateTime cutoff, int limit) {
        return incidents.values().stream()
                .filter(i -> i.getStatus() == IncidentStatus.ACTIVE || i.getStatus() == IncidentStatus.ESCALATED)
                .filter(i -> i.getLastSeenAt() != null && i.getLastSeenAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Incident::getLastSeenAt))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<Incident> findAll() {
        return incidents.values().stream()
                .sorted(Comparator.comparing(Incident::getLastSeenAt).reversed())
                .toList();
    }

    @Override
    public List<Incident> search(IncidentQuery query) {
        return incidents.values().stream()
                .filter(i -> matches(query.getTenantId(), i.getTenantId()))
                .filter(i -> matches(query.getSourceSystem(), i.getSourceSystem()))
                .filter(i -> matches(query.getSiteId(), i.getSiteId()))
                .filter(i -> matches(query.getPlantId(), i.getPlantId()))
                .filter(i -> matches(query.getObjectType(), i.getObjectType()))
                .filter(i -> matches(query.getObjectId(), i.getObjectId()))
                .filter(i -> matches(query.getEventType(), i.getEventType()))
                .filter(i -> matches(query.getErrorCode(), i.getErrorCode()))
                .filter(i -> query.getSeverity() == null || query.getSeverity() == i.getSeverity())
                .filter(i -> query.getStatus() == null || query.getStatus() == i.getStatus())
                .sorted(Comparator.comparing(Incident::getLastSeenAt).reversed())
                .limit(query.getLimit())
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(actual == null ? "" : actual);
    }
}
