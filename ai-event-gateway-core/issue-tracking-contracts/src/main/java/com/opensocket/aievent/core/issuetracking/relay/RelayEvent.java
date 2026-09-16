package com.opensocket.aievent.core.issuetracking.relay;
import java.time.OffsetDateTime; import java.util.Objects;
public record RelayEvent(String tenantId,String eventId,String aggregateType,String aggregateId,RelayEventType eventType,String actorId,String reasonCode,String metadataJson,String previousEventHash,String eventHash,OffsetDateTime occurredAt,String correlationId) {
 public RelayEvent { tenantId=req(tenantId,"tenantId");eventId=req(eventId,"eventId");aggregateType=req(aggregateType,"aggregateType");aggregateId=req(aggregateId,"aggregateId");eventType=Objects.requireNonNull(eventType,"eventType is required");actorId=norm(actorId);reasonCode=req(reasonCode,"reasonCode");metadataJson=norm(metadataJson);previousEventHash=norm(previousEventHash);eventHash=req(eventHash,"eventHash");occurredAt=Objects.requireNonNull(occurredAt,"occurredAt is required");correlationId=norm(correlationId); }
 private static String req(String v,String n){Objects.requireNonNull(v,n+" is required");String x=v.trim();if(x.isEmpty())throw new IllegalArgumentException(n+" is required");return x;} private static String norm(String v){return v==null?"":v.trim();}
}
