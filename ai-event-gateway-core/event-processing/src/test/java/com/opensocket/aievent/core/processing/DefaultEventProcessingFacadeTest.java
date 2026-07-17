package com.opensocket.aievent.core.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.DedupStateStore;
import com.opensocket.aievent.core.dedup.InMemoryDedupStateStore;
import com.opensocket.aievent.core.dedup.cache.DedupStateCache;
import com.opensocket.aievent.core.dedup.snapshot.DedupStateSnapshotRepository;
import com.opensocket.aievent.core.dedup.snapshot.NoopDedupStateSnapshotRepository;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.fingerprint.FingerprintGenerator;
import com.opensocket.aievent.core.incident.DefaultIncidentFacade;
import com.opensocket.aievent.core.incident.InMemoryIncidentRepository;
import com.opensocket.aievent.core.incident.IncidentManager;
import com.opensocket.aievent.core.normalize.EventNormalizer;
import com.opensocket.aievent.core.summary.InMemoryIncidentOccurrenceSummaryRepository;

class DefaultEventProcessingFacadeTest {
    @Test
    void shouldAggregateThroughIncidentFacadeWithoutRepositoryCoupling() {
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        EventProcessingFacade facade = new DefaultEventProcessingFacade(
                new EventNormalizer(),
                new FingerprintGenerator(),
                new InMemoryDedupStateStore(),
                new NoopDedupStateSnapshotRepository(),
                new DefaultIncidentFacade(
                        new IncidentManager(incidents),
                        incidents,
                        new InMemoryIncidentOccurrenceSummaryRepository()),
                new EventProcessingProperties());

        EventProcessingResult first = facade.process(request("Order SO202606110001 failed at 10:01:00"));
        EventProcessingResult second = facade.process(request("Order SO202606110999 failed at 10:02:00"));

        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.incident().getIncidentId()).isEqualTo(first.incident().getIncidentId());
        assertThat(second.dedup().duplicate()).isTrue();
        assertThat(second.dedup().state().getOccurrenceCount()).isEqualTo(2);
        assertThat(incidents.findAll()).hasSize(1);
    }


    @Test
    void shouldAttachDedupIncidentOnlyAfterTransactionCommit() {
        TrackingDedupStateStore dedup = new TrackingDedupStateStore();
        TrackingDedupSnapshotRepository snapshots = new TrackingDedupSnapshotRepository();
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        EventProcessingFacade facade = new DefaultEventProcessingFacade(
                new EventNormalizer(),
                new FingerprintGenerator(),
                dedup,
                snapshots,
                new DefaultIncidentFacade(
                        new IncidentManager(incidents),
                        incidents,
                        new InMemoryIncidentOccurrenceSummaryRepository()),
                new EventProcessingProperties());

        TransactionSynchronizationManager.initSynchronization();
        try {
            facade.process(request("Order SO202606110001 failed at 10:01:00"));
            assertThat(dedup.attachedIncidentId).isNull();
            assertThat(snapshots.saved).isFalse();

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

            assertThat(dedup.attachedIncidentId).isNotBlank();
            assertThat(snapshots.saved).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }


    @Test
    void dbSourceOfTruthShouldAttachIncidentInsideTransactionAndPublishCacheAfterCommit() {
        TrackingDedupStateStore dedup = new TrackingDedupStateStore(true);
        TrackingDedupSnapshotRepository snapshots = new TrackingDedupSnapshotRepository();
        TrackingDedupStateCache cache = new TrackingDedupStateCache();
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        EventProcessingFacade facade = new DefaultEventProcessingFacade(
                new EventNormalizer(),
                new FingerprintGenerator(),
                dedup,
                snapshots,
                cache,
                new DefaultIncidentFacade(
                        new IncidentManager(incidents),
                        incidents,
                        new InMemoryIncidentOccurrenceSummaryRepository()),
                new EventProcessingProperties());

        TransactionSynchronizationManager.initSynchronization();
        try {
            facade.process(request("Order SO202606110001 failed at 10:01:00"));

            assertThat(dedup.attachedIncidentId).isNotBlank();
            assertThat(cache.published).isFalse();
            assertThat(snapshots.saved).isFalse();

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

            assertThat(snapshots.saved).isTrue();
            assertThat(cache.published).isTrue();
            assertThat(cache.state.getActiveIncidentId()).isEqualTo(dedup.attachedIncidentId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private EventIntakeRequest request(String message) {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("ERP");
        request.setSiteId("TNN");
        request.setPlantId("FAB-01");
        request.setObjectType("ORDER");
        request.setObjectId("SO-DYNAMIC");
        request.setEventType("ORDER_FAILED");
        request.setErrorCode("E1001");
        request.setSeverity("HIGH");
        request.setMessage(message);
        return request;
    }

    private static final class TrackingDedupStateStore implements DedupStateStore {
        private final boolean transactionalSourceOfTruth;
        private DedupState state;
        private String attachedIncidentId;

        private TrackingDedupStateStore() {
            this(false);
        }

        private TrackingDedupStateStore(boolean transactionalSourceOfTruth) {
            this.transactionalSourceOfTruth = transactionalSourceOfTruth;
        }

        @Override
        public DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl) {
            state = new DedupState(fingerprint, null, event.occurredAt(), event.occurredAt(), 1,
                    event.severity(), event.eventId(), event.normalizedMessage());
            return new DedupDecision(false, state, "tracking dedup state");
        }

        @Override
        public void attachIncident(String fingerprint, String incidentId, Duration ttl) {
            attachedIncidentId = incidentId;
            state.setActiveIncidentId(incidentId);
        }

        @Override
        public Optional<DedupState> find(String fingerprint) {
            return Optional.ofNullable(state);
        }

        @Override
        public String mode() {
            return transactionalSourceOfTruth ? "MYBATIS" : "TRACKING_TEST";
        }

        @Override
        public boolean transactionalSourceOfTruth() {
            return transactionalSourceOfTruth;
        }
    }

    private static final class TrackingDedupStateCache implements DedupStateCache {
        private boolean published;
        private DedupState state;

        @Override
        public void publish(DedupState state, NormalizedEvent event, Duration ttl) {
            this.published = true;
            this.state = state;
        }

        @Override
        public String mode() {
            return "TRACKING_TEST";
        }
    }

    private static final class TrackingDedupSnapshotRepository implements DedupStateSnapshotRepository {
        private boolean saved;

        @Override
        public void saveSnapshot(DedupState state, NormalizedEvent event, Duration ttl) {
            saved = true;
        }

        @Override
        public String mode() {
            return "TRACKING_SNAPSHOT_TEST";
        }
    }

}
