package com.opensocket.aievent.core.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.DedupStateStore;
import com.opensocket.aievent.core.dedup.cache.DedupStateCache;
import com.opensocket.aievent.core.dedup.cache.NoopDedupStateCache;
import com.opensocket.aievent.core.dedup.snapshot.DedupStateSnapshotRepository;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.fingerprint.FingerprintGenerator;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentObservationCommand;
import com.opensocket.aievent.core.normalize.EventNormalizer;

@Service
public class DefaultEventProcessingFacade implements EventProcessingFacade, EventProcessingOperationalQuery {
    private static final Logger log = LoggerFactory.getLogger(DefaultEventProcessingFacade.class);

    private final EventNormalizer normalizer;
    private final FingerprintGenerator fingerprintGenerator;
    private final DedupStateStore dedupStateStore;
    private final DedupStateSnapshotRepository dedupStateSnapshotRepository;
    private final DedupStateCache dedupStateCache;
    private final IncidentFacade incidentFacade;
    private final EventProcessingProperties properties;

    public DefaultEventProcessingFacade(EventNormalizer normalizer,
                                        FingerprintGenerator fingerprintGenerator,
                                        DedupStateStore dedupStateStore,
                                        DedupStateSnapshotRepository dedupStateSnapshotRepository,
                                        IncidentFacade incidentFacade,
                                        EventProcessingProperties properties) {
        this(normalizer, fingerprintGenerator, dedupStateStore, dedupStateSnapshotRepository,
                new NoopDedupStateCache(), incidentFacade, properties);
    }

    @Autowired
    public DefaultEventProcessingFacade(EventNormalizer normalizer,
                                        FingerprintGenerator fingerprintGenerator,
                                        DedupStateStore dedupStateStore,
                                        DedupStateSnapshotRepository dedupStateSnapshotRepository,
                                        DedupStateCache dedupStateCache,
                                        IncidentFacade incidentFacade,
                                        EventProcessingProperties properties) {
        this.normalizer = normalizer;
        this.fingerprintGenerator = fingerprintGenerator;
        this.dedupStateStore = dedupStateStore;
        this.dedupStateSnapshotRepository = dedupStateSnapshotRepository;
        this.dedupStateCache = dedupStateCache;
        this.incidentFacade = incidentFacade;
        this.properties = properties;
    }

    @Override
    @Transactional
    public EventProcessingResult process(EventIntakeRequest request) {
        NormalizedEvent event = normalizer.normalize(request);
        log.info("event_processing_started eventId={} tenantId={} sourceSystem={} eventStage={} eventType={} requestedSkill={} correlationId={}",
                event.eventId(), event.tenantId(), event.sourceSystem(), event.eventStage(), event.eventType(), event.requestedSkill(), event.correlationId());
        String fingerprint = fingerprintGenerator.generate(event);
        DedupDecision dedup = dedupStateStore.touch(
                fingerprint,
                event,
                properties.getDedupWindow(),
                properties.getDedupTtl());

        Incident incident = incidentFacade.observe(new IncidentObservationCommand(
                fingerprint,
                event,
                dedup.state().getFirstSeenAt(),
                dedup.state().getLastSeenAt(),
                dedup.state().getOccurrenceCount()));
        log.info("event_processing_observed eventId={} fingerprint={} duplicate={} occurrenceCount={} incidentId={} dedupReason={}",
                event.eventId(), fingerprint, dedup.duplicate(), dedup.state().getOccurrenceCount(), incident.getIncidentId(), dedup.reason());

        if (dedupStateStore.transactionalSourceOfTruth()) {
            dedupStateStore.attachIncident(fingerprint, incident.getIncidentId(), properties.getDedupTtl());
            DedupState state = dedupStateStore.find(fingerprint).orElse(dedup.state());
            runAfterCommit(() -> publishCommittedDedupState(state, event));
        } else {
            runAfterCommit(() -> {
                dedupStateStore.attachIncident(fingerprint, incident.getIncidentId(), properties.getDedupTtl());
                dedupStateStore.find(fingerprint).ifPresent(state -> publishCommittedDedupState(state, event));
            });
        }

        return new EventProcessingResult(event, fingerprint, dedup, incident);
    }

    /**
     * Publish non-authoritative read models only after the database transaction commits.
     * In P1-C the DB-backed dedup store can be the authoritative state, while Redis /
     * Redisson is a cache and the legacy event_dedup_state snapshot remains an external
     * read model for non-DB stores.
     */
    private void publishCommittedDedupState(DedupState state, NormalizedEvent event) {
        log.debug("event_processing_after_commit eventId={} fingerprint={} dedupMode={} snapshotMode={} cachePublished=true",
                event == null ? null : event.eventId(), state == null ? null : state.getFingerprint(),
                dedupStateStore.mode(), dedupStateSnapshotRepository.mode());
        if (!sameMode(dedupStateStore.mode(), dedupStateSnapshotRepository.mode())) {
            dedupStateSnapshotRepository.saveSnapshot(state, event, properties.getDedupTtl());
        }
        dedupStateCache.publish(state, event, properties.getDedupTtl());
    }

    /**
     * Redis/Redisson dedup hot state is not enlisted in the PostgreSQL transaction.
     * Incident attachment and persistent snapshot/cache publication therefore run only after
     * the database transaction commits. This prevents Redis from advertising an
     * incident id that was rolled back in the relational source of truth.
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private boolean sameMode(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    @Override
    public String dedupStoreMode() {
        return dedupStateStore.mode();
    }

    @Override
    public String dedupSnapshotStoreMode() {
        return dedupStateSnapshotRepository.mode();
    }

}
