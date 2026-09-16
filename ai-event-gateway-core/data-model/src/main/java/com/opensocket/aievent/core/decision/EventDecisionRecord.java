package com.opensocket.aievent.core.decision;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class EventDecisionRecord {
    @ToString.Include private String eventId;
    private String tenantId;
    private String sourceSystem;
    private String eventType;
    private String eventStage;
    private String correlationId;
    private String normalizedMessage;
    private Map<String,Object> payload;
    private OffsetDateTime occurredAt;
    @ToString.Include private String fingerprint;
    @ToString.Include private String incidentId;
    private DecisionType decisionType;
    private boolean duplicate;
    private long occurrenceCount;
    private List<DecisionAction> actions;
    private String reason;
    private OffsetDateTime decidedAt;
    private String ownerDepartmentId;
    private String ownerGroupId;
    private String scopeStatus;
    private Long scopeSourceVersion;
    private OffsetDateTime scopeInheritedAt;
    public void setEventId(String v){eventId=v;} public void setTenantId(String v){tenantId=v;} public void setSourceSystem(String v){sourceSystem=v;}
    public void setEventType(String v){eventType=v;} public void setEventStage(String v){eventStage=v;} public void setCorrelationId(String v){correlationId=v;}
    public void setNormalizedMessage(String v){normalizedMessage=v;} public void setPayload(Map<String,Object> v){payload=v;} public void setOccurredAt(OffsetDateTime v){occurredAt=v;}
    public void setFingerprint(String v){fingerprint=v;} public void setIncidentId(String v){incidentId=v;} public void setDecisionType(DecisionType v){decisionType=v;}
    public void setDuplicate(boolean v){duplicate=v;} public void setOccurrenceCount(long v){occurrenceCount=v;} public void setActions(List<DecisionAction> v){actions=v;}
    public void setReason(String v){reason=v;} public void setDecidedAt(OffsetDateTime v){decidedAt=v;} public void setOwnerDepartmentId(String v){ownerDepartmentId=v;}
    public void setOwnerGroupId(String v){ownerGroupId=v;} public void setScopeStatus(String v){scopeStatus=v;} public void setScopeSourceVersion(Long v){scopeSourceVersion=v;}
    public void setScopeInheritedAt(OffsetDateTime v){scopeInheritedAt=v;}
}
