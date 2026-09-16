package com.opensocket.aievent.core.issuetracking.change;
import java.time.OffsetDateTime; import java.util.Objects;
public record ProviderActionCandidateEvent(String tenantId,String eventId,String candidateId,ProviderActionCandidateEventType eventType,String actorId,String reasonCode,String metadataJson,String previousEventHash,String eventHash,OffsetDateTime occurredAt,String correlationId) {
 public ProviderActionCandidateEvent { tenantId=req(tenantId,"tenantId");eventId=req(eventId,"eventId");candidateId=req(candidateId,"candidateId");eventType=Objects.requireNonNull(eventType,"eventType is required");actorId=norm(actorId);reasonCode=req(reasonCode,"reasonCode");metadataJson=metadataJson==null?"{}":metadataJson;previousEventHash=norm(previousEventHash);eventHash=req(eventHash,"eventHash");occurredAt=Objects.requireNonNull(occurredAt,"occurredAt is required");correlationId=norm(correlationId); }
 private static String req(String v,String n){Objects.requireNonNull(v,n+" is required");String x=v.trim();if(x.isEmpty())throw new IllegalArgumentException(n+" is required");return x;} private static String norm(String v){return v==null?"":v.trim();}
}
