package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Append-only Phase 8 execution state/evidence event. */
public record PlanExecutionEvent(String eventId,String tenantId,String runId,String stepId,String eventType,String fromState,String toState,String reason,String actorRef,Map<String,Object> evidence,OffsetDateTime occurredAt) { public PlanExecutionEvent { evidence=evidence==null?Map.of():Map.copyOf(evidence); } }
