package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
public record HumanReadableAuditResponse(String eventId,String category,String action,String summary,String actorId,String actorName,String targetType,String targetId,String targetName,String outcome,String reason,String correlationId,Instant occurredAt,String technicalEvidence){}
