package com.opensocket.aievent.core.decision;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class EventDecisionRecord {
    @ToString.Include
    private String eventId;
    @ToString.Include
    private String fingerprint;
    @ToString.Include
    private String incidentId;
    private DecisionType decisionType;
    private boolean duplicate;
    private long occurrenceCount;
    private List<DecisionAction> actions;
    private String reason;
    private OffsetDateTime decidedAt;
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public void setActions(List<DecisionAction> actions) { this.actions = actions; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
}
