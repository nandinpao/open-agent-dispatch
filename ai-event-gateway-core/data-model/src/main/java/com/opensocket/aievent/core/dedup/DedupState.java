package com.opensocket.aievent.core.dedup;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.event.EventSeverity;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class DedupState {
    @ToString.Include
    private String fingerprint;
    @ToString.Include
    private String activeIncidentId;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
    private long occurrenceCount;
    private EventSeverity maxSeverity;
    @ToString.Include
    private String lastEventId;
    private String lastMessage;

    public DedupState() {
    }

    public DedupState(String fingerprint, String activeIncidentId, OffsetDateTime firstSeenAt,
                      OffsetDateTime lastSeenAt, long occurrenceCount, EventSeverity maxSeverity,
                      String lastEventId, String lastMessage) {
        this.fingerprint = fingerprint;
        this.activeIncidentId = activeIncidentId;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.occurrenceCount = occurrenceCount;
        this.maxSeverity = maxSeverity;
        this.lastEventId = lastEventId;
        this.lastMessage = lastMessage;
    }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public void setActiveIncidentId(String activeIncidentId) { this.activeIncidentId = activeIncidentId; }
    public void setFirstSeenAt(OffsetDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public void setMaxSeverity(EventSeverity maxSeverity) { this.maxSeverity = maxSeverity; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
}
