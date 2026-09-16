package com.opensocket.aievent.core.governance;
import java.time.OffsetDateTime; import java.util.Map;
public record AuditEvidence(String tenantId,String evidenceId,String eventType,String aggregateType,String aggregateId,String rootTaskId,String actorType,String actorId,String action,String reasonCode,String auditReason,String correlationId,String causationId,String authorizationDecisionId,String requestId,String clientAddress,String payloadHash,String outcome,Map<String,Object> evidence,OffsetDateTime occurredAt){public AuditEvidence{evidence=evidence==null?Map.of():Map.copyOf(evidence);}}
