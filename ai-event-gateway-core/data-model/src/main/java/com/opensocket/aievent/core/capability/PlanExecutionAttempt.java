package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Append-oriented execution-attempt evidence. */
public record PlanExecutionAttempt(String attemptId,String tenantId,String runId,String stepId,int attemptNo,String idempotencyKey,String state,String adapterResolutionId,String childTaskRef,String externalExecutionRef,List<String> reasonCodes,OffsetDateTime submittedAt,OffsetDateTime deadlineAt,OffsetDateTime completedAt) { public PlanExecutionAttempt { reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes); } }
