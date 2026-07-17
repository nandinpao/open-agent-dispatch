package com.opensocket.aievent.core.incident;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.event.EventSeverity;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class Incident {
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
    private EventSeverity severity;
    private IncidentStatus status;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
    private long occurrenceCount;
    private String lastMessage;
    private String linkedTaskId;
    private String linkedIssueId;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime reopenedAt;
    private int reopenCount;
    private String lifecycleReason;
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
    public void setSeverity(EventSeverity severity) { this.severity = severity; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public void setFirstSeenAt(OffsetDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public void setLinkedTaskId(String linkedTaskId) { this.linkedTaskId = linkedTaskId; }
    public void setLinkedIssueId(String linkedIssueId) { this.linkedIssueId = linkedIssueId; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setReopenedAt(OffsetDateTime reopenedAt) { this.reopenedAt = reopenedAt; }
    public void setReopenCount(int reopenCount) { this.reopenCount = Math.max(0, reopenCount); }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
}
