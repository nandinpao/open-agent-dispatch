package com.opensocket.aievent.database.persistence.eventprocessing.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DedupStateSnapshotPo {
    private String fingerprint; private String activeIncidentId; private String tenantId; private String sourceSystem; private String siteId; private String plantId;
    private String objectType; private String objectId; private String eventType; private String errorCode; private OffsetDateTime firstSeenAt; private OffsetDateTime lastSeenAt;
    private long occurrenceCount; private String maxSeverity; private String lastEventId; private String lastMessage; private OffsetDateTime expiresAt;
}
