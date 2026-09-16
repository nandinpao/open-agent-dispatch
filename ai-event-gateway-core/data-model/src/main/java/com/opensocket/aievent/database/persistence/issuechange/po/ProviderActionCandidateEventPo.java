package com.opensocket.aievent.database.persistence.issuechange.po;
import java.time.OffsetDateTime;

public class ProviderActionCandidateEventPo {
 private String tenantId;
 private String eventId;
 private String candidateId;
 private String eventType;
 private String actorId;
 private String reasonCode;
 private String metadataJson;
 private String previousEventHash;
 private String eventHash;
 private OffsetDateTime occurredAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getEventId(){return eventId;} public void setEventId(String value){this.eventId=value;}
 public String getCandidateId(){return candidateId;} public void setCandidateId(String value){this.candidateId=value;}
 public String getEventType(){return eventType;} public void setEventType(String value){this.eventType=value;}
 public String getActorId(){return actorId;} public void setActorId(String value){this.actorId=value;}
 public String getReasonCode(){return reasonCode;} public void setReasonCode(String value){this.reasonCode=value;}
 public String getMetadataJson(){return metadataJson;} public void setMetadataJson(String value){this.metadataJson=value;}
 public String getPreviousEventHash(){return previousEventHash;} public void setPreviousEventHash(String value){this.previousEventHash=value;}
 public String getEventHash(){return eventHash;} public void setEventHash(String value){this.eventHash=value;}
 public OffsetDateTime getOccurredAt(){return occurredAt;} public void setOccurredAt(OffsetDateTime value){this.occurredAt=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
}
