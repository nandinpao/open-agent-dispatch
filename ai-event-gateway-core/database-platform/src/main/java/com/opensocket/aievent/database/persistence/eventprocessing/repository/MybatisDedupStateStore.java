package com.opensocket.aievent.database.persistence.eventprocessing.repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.DedupStateStore;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.database.persistence.eventprocessing.converter.DedupStateSnapshotPersistenceConverter;
import com.opensocket.aievent.database.persistence.eventprocessing.dao.DedupStateSnapshotDao;
import com.opensocket.aievent.database.persistence.eventprocessing.po.DedupStateSnapshotPo;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "event.dedup", name = "store", havingValue = "MYBATIS")
public class MybatisDedupStateStore implements DedupStateStore {
    private final DedupStateSnapshotDao dao;
    private final DedupStateSnapshotPersistenceConverter converter;

    public MybatisDedupStateStore(DedupStateSnapshotDao dao, DedupStateSnapshotPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    @Transactional
    public DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl) {
        OffsetDateTime occurredAt = effectiveOccurredAt(event);
        DedupState initialState = new DedupState(
                fingerprint,
                null,
                occurredAt,
                occurredAt,
                1,
                event.severity(),
                event.eventId(),
                event.normalizedMessage());
        DedupStateSnapshotPo initial = converter.toPo(initialState, event, ttl);

        int inserted = dao.insertIfAbsent(initial);
        DedupStateSnapshotPo current = dao.findForUpdate(fingerprint);
        if (current == null) {
            throw new IllegalStateException("Unable to load DB dedup state after insert for fingerprint=" + fingerprint);
        }
        if (inserted > 0) {
            return new DedupDecision(false, toState(current), "no active DB dedup state");
        }

        if (isExpired(current, occurredAt)) {
            DedupStateSnapshotPo reset = converter.toPo(initialState, event, ttl);
            reset.setActiveIncidentId(null);
            dao.updateState(reset);
            return new DedupDecision(false, toState(reset), "expired DB dedup state");
        }

        boolean duplicate = isWithinDuplicateWindow(current.getLastSeenAt(), occurredAt, duplicateWindow);
        DedupStateSnapshotPo updated = merge(current, event, occurredAt, ttl);
        dao.updateState(updated);
        return new DedupDecision(
                duplicate,
                toState(updated),
                duplicate ? "same fingerprint within DB duplicate window" : "same fingerprint outside DB duplicate window");
    }

    @Override
    @Transactional
    public void attachIncident(String fingerprint, String incidentId, Duration ttl) {
        dao.attachIncident(fingerprint, incidentId, OffsetDateTime.now(ZoneOffset.UTC).plus(ttl));
    }

    @Override
    public Optional<DedupState> find(String fingerprint) {
        return Optional.ofNullable(dao.findActiveByFingerprint(fingerprint)).map(this::toState);
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    @Override
    public boolean transactionalSourceOfTruth() {
        return true;
    }

    private DedupStateSnapshotPo merge(DedupStateSnapshotPo current, NormalizedEvent event, OffsetDateTime occurredAt, Duration ttl) {
        DedupStateSnapshotPo updated = new DedupStateSnapshotPo();
        updated.setFingerprint(current.getFingerprint());
        updated.setActiveIncidentId(current.getActiveIncidentId());
        updated.setTenantId(event.tenantId());
        updated.setSourceSystem(event.sourceSystem());
        updated.setSiteId(event.siteId());
        updated.setPlantId(event.plantId());
        updated.setObjectType(event.objectType());
        updated.setObjectId(event.objectId());
        updated.setEventType(event.eventType());
        updated.setErrorCode(event.errorCode());
        updated.setFirstSeenAt(current.getFirstSeenAt() == null ? occurredAt : current.getFirstSeenAt());
        updated.setLastSeenAt(max(current.getLastSeenAt(), occurredAt));
        updated.setOccurrenceCount(current.getOccurrenceCount() + 1);
        updated.setMaxSeverity(maxSeverity(current.getMaxSeverity(), event.severity()).name());
        updated.setLastEventId(event.eventId());
        updated.setLastMessage(event.normalizedMessage());
        updated.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(ttl));
        return updated;
    }

    private DedupState toState(DedupStateSnapshotPo po) {
        return new DedupState(
                po.getFingerprint(),
                po.getActiveIncidentId(),
                po.getFirstSeenAt(),
                po.getLastSeenAt(),
                po.getOccurrenceCount(),
                EventSeverity.parse(po.getMaxSeverity()),
                po.getLastEventId(),
                po.getLastMessage());
    }

    private boolean isExpired(DedupStateSnapshotPo po, OffsetDateTime occurredAt) {
        return po.getExpiresAt() != null && !po.getExpiresAt().isAfter(occurredAt);
    }

    private boolean isWithinDuplicateWindow(OffsetDateTime previousLastSeenAt, OffsetDateTime occurredAt, Duration duplicateWindow) {
        if (previousLastSeenAt == null) {
            return false;
        }
        return !previousLastSeenAt.plus(duplicateWindow).isBefore(occurredAt);
    }

    private EventSeverity maxSeverity(String current, EventSeverity incoming) {
        EventSeverity currentSeverity = EventSeverity.parse(current);
        EventSeverity incomingSeverity = incoming == null ? EventSeverity.MEDIUM : incoming;
        return incomingSeverity.higherThan(currentSeverity) ? incomingSeverity : currentSeverity;
    }

    private OffsetDateTime max(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return right.isAfter(left) ? right : left;
    }

    private OffsetDateTime effectiveOccurredAt(NormalizedEvent event) {
        return event.occurredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : event.occurredAt();
    }
}
