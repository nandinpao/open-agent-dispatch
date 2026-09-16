package com.opensocket.aievent.core.a2a;
public record A2ARepairExecutionCommand(String tenantId,String caseId,long expectedVersion,String planHash,String idempotencyKey,String actorId,String reason){}
