package com.opensocket.aievent.database.persistence.eventprocessing.converter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.database.persistence.eventprocessing.po.DedupStateSnapshotPo;

@DatabasePersistenceConverter
public class DedupStateSnapshotPersistenceConverter {

    public DedupStateSnapshotPo toPo(DedupState state, NormalizedEvent event, Duration ttl) {
        DedupStateSnapshotPo po = new DedupStateSnapshotPo();
        po.setFingerprint(state.getFingerprint());
        po.setActiveIncidentId(state.getActiveIncidentId());
        po.setTenantId(event.tenantId());
        po.setSourceSystem(event.sourceSystem());
        po.setSiteId(event.siteId());
        po.setPlantId(event.plantId());
        po.setObjectType(event.objectType());
        po.setObjectId(event.objectId());
        po.setEventType(event.eventType());
        po.setErrorCode(event.errorCode());
        po.setFirstSeenAt(state.getFirstSeenAt());
        po.setLastSeenAt(state.getLastSeenAt());
        po.setOccurrenceCount(state.getOccurrenceCount());
        po.setMaxSeverity(state.getMaxSeverity() == null ? null : state.getMaxSeverity().name());
        po.setLastEventId(state.getLastEventId());
        po.setLastMessage(state.getLastMessage());
        po.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(ttl));
        return po;
    }
}
