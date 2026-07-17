package com.opensocket.aievent.core.summary;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.event.EventSeverity;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class IncidentOccurrenceSummary {
    @ToString.Include
    private String summaryId;
    @ToString.Include
    private String incidentId;
    @ToString.Include
    private String fingerprint;
    private String tenantId;
    private String sourceSystem;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private OffsetDateTime windowStart;
    private OffsetDateTime windowEnd;
    private long occurrenceCount;
    private EventSeverity maxSeverity;
    private String latestEventId;
    private String latestMessage;
    private String latestPayloadJson;
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setWindowStart(OffsetDateTime windowStart) { this.windowStart = windowStart; }
    public void setWindowEnd(OffsetDateTime windowEnd) { this.windowEnd = windowEnd; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public void setMaxSeverity(EventSeverity maxSeverity) { this.maxSeverity = maxSeverity; }
    public void setLatestEventId(String latestEventId) { this.latestEventId = latestEventId; }
    public void setLatestMessage(String latestMessage) { this.latestMessage = latestMessage; }
    public void setLatestPayloadJson(String latestPayloadJson) { this.latestPayloadJson = latestPayloadJson; }
}
