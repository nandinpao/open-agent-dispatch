package com.opensocket.aievent.database.persistence.incident.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class IncidentOccurrenceSummaryPo {
    private String summaryId; private String incidentId; private String fingerprint; private String tenantId; private String sourceSystem; private String siteId; private String plantId;
    private String objectType; private String objectId; private String eventType; private String errorCode; private OffsetDateTime windowStart; private OffsetDateTime windowEnd;
    private long occurrenceCount; private String maxSeverity; private String latestEventId; private String latestMessage; private String latestPayloadJson;
}
