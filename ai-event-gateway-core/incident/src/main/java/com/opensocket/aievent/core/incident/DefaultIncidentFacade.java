package com.opensocket.aievent.core.incident;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummaryRepository;
import com.opensocket.aievent.core.summary.IncidentSummaryProperties;

@Service
public class DefaultIncidentFacade implements IncidentFacade, IncidentOperationalQuery {
    private final IncidentManager incidentManager;
    private final IncidentRepository incidentRepository;
    private final IncidentOccurrenceSummaryRepository occurrenceSummaryRepository;
    private final IncidentSummaryProperties summaryProperties;
    private final ModuleEventPublisher eventPublisher;

    public DefaultIncidentFacade(IncidentManager incidentManager,
                                 IncidentRepository incidentRepository,
                                 IncidentOccurrenceSummaryRepository occurrenceSummaryRepository) {
        this(incidentManager, incidentRepository, occurrenceSummaryRepository,
                new IncidentSummaryProperties(), ModuleEventPublisher.noop());
    }

    @Autowired
    public DefaultIncidentFacade(IncidentManager incidentManager,
                                 IncidentRepository incidentRepository,
                                 IncidentOccurrenceSummaryRepository occurrenceSummaryRepository,
                                 IncidentSummaryProperties summaryProperties,
                                 ObjectProvider<ModuleEventPublisher> eventPublisherProvider) {
        this(incidentManager, incidentRepository, occurrenceSummaryRepository, summaryProperties,
                eventPublisherProvider.getIfAvailable(ModuleEventPublisher::noop));
    }

    private DefaultIncidentFacade(IncidentManager incidentManager,
                                  IncidentRepository incidentRepository,
                                  IncidentOccurrenceSummaryRepository occurrenceSummaryRepository,
                                  IncidentSummaryProperties summaryProperties,
                                  ModuleEventPublisher eventPublisher) {
        this.incidentManager = incidentManager;
        this.incidentRepository = incidentRepository;
        this.occurrenceSummaryRepository = occurrenceSummaryRepository;
        this.summaryProperties = summaryProperties == null
                ? new IncidentSummaryProperties()
                : summaryProperties;
        this.eventPublisher = eventPublisher == null ? ModuleEventPublisher.noop() : eventPublisher;
    }

    @Override
    @Transactional
    public Incident observe(IncidentObservationCommand command) {
        Incident before = incidentRepository.findActiveByFingerprint(command.fingerprint()).orElse(null);
        IncidentStatus previousStatus = before == null ? null : before.getStatus();
        EventSeverity previousSeverity = before == null ? null : before.getSeverity();
        Incident incident = incidentManager.getOrCreate(command);
        occurrenceSummaryRepository.recordOccurrence(
                incident, command.event(), command.fingerprint(), summaryProperties.getWindow());
        if (incident.getStatus() == IncidentStatus.ESCALATED
                && (previousStatus != IncidentStatus.ESCALATED || previousSeverity != incident.getSeverity())) {
            eventPublisher.publish(new IncidentEscalatedEvent(
                    "evt-" + UUID.randomUUID(),
                    incident.getIncidentId(),
                    incident.getFingerprint(),
                    incident.getSeverity() == null ? null : incident.getSeverity().name(),
                    incident.getOccurrenceCount(),
                    command.event().eventId(),
                    incident.getTenantId(),
                    incident.getSiteId(),
                    OffsetDateTime.now(ZoneOffset.UTC)));
        }
        return incident;
    }

    @Override
    @Transactional
    public Incident linkTaskIfAbsent(String incidentId, String taskId) {
        Incident incident = require(incidentId);
        if ((incident.getLinkedTaskId() == null || incident.getLinkedTaskId().isBlank())
                && taskId != null && !taskId.isBlank()) {
            incident.setLinkedTaskId(taskId);
            return incidentRepository.save(incident);
        }
        return incident;
    }

    @Override
    @Transactional
    public Incident linkIssueIfAbsent(String incidentId, String issueId) {
        Incident incident = require(incidentId);
        if ((incident.getLinkedIssueId() == null || incident.getLinkedIssueId().isBlank())
                && issueId != null && !issueId.isBlank()) {
            incident.setLinkedIssueId(issueId.trim());
            return incidentRepository.save(incident);
        }
        return incident;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Incident> findById(String incidentId) {
        return incidentRepository.findById(incidentId);
    }

    @Override
    @Transactional
    public Incident resolve(String incidentId, String reason, OffsetDateTime now) {
        Incident incident = require(incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) return incident;
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(effectiveNow);
        incident.setLifecycleReason(firstNonBlank(reason, "Incident resolved"));
        return incidentRepository.save(incident);
    }

    @Override
    @Transactional
    public Incident reopen(String incidentId, String reason, OffsetDateTime now) {
        Incident incident = require(incidentId);
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setResolvedAt(null);
        incident.setReopenedAt(effectiveNow);
        incident.setReopenCount(incident.getReopenCount() + 1);
        incident.setLifecycleReason(firstNonBlank(reason, "Incident reopened"));
        return incidentRepository.save(incident);
    }

    @Override
    @Transactional
    public Incident suppress(String incidentId, String reason, OffsetDateTime now) {
        Incident incident = require(incidentId);
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        incident.setStatus(IncidentStatus.SUPPRESSED);
        incident.setResolvedAt(effectiveNow);
        incident.setLifecycleReason(firstNonBlank(reason, "Incident suppressed"));
        return incidentRepository.save(incident);
    }

    @Override
    @Transactional
    public LifecycleScanResult autoResolveStale(OffsetDateTime cutoff, int limit, String reason, OffsetDateTime now) {
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        List<Incident> stale = incidentRepository.findActiveLastSeenBefore(cutoff, Math.max(1, limit));
        int updated = 0;
        for (Incident incident : stale) {
            if (incident.getStatus() != IncidentStatus.RESOLVED) {
                incident.setStatus(IncidentStatus.RESOLVED);
                incident.setResolvedAt(effectiveNow);
                incident.setLifecycleReason(firstNonBlank(reason, "Incident auto-resolved"));
                incidentRepository.save(incident);
                updated++;
            }
        }
        LifecycleScanResult result = new LifecycleScanResult();
        result.setScanned(stale.size());
        result.setUpdated(updated);
        result.setMessage("Auto-resolved stale incidents before " + cutoff);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> search(IncidentQuery query) {
        return incidentRepository.search(query == null ? new IncidentQuery() : query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.opensocket.aievent.core.summary.IncidentOccurrenceSummary> occurrenceSummary(String incidentId, int limit) {
        return occurrenceSummaryRepository.findByIncidentId(incidentId, Math.max(1, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> statusCounts(int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            IncidentQuery query = new IncidentQuery();
            query.setStatus(status);
            query.setLimit(Math.max(1, limit));
            counts.put(status.name(), incidentRepository.search(query).size());
        }
        return counts;
    }

    @Override public String incidentStoreMode() { return incidentRepository.mode(); }
    @Override public String occurrenceSummaryStoreMode() { return occurrenceSummaryRepository.mode(); }

    private Incident require(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
