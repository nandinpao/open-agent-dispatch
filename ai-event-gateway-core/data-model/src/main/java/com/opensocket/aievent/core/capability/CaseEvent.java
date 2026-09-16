package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.Map;
public record CaseEvent(String tenantId,String eventId,String caseId,String eventType,String actorRef,String reason,Map<String,Object> evidence,OffsetDateTime occurredAt){public CaseEvent{evidence=evidence==null?Map.of():Map.copyOf(evidence);}}
