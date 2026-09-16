package com.opensocket.aievent.core.a2a;
public record A2ARepairOutcomeCommand(String tenantId,String caseId,long expectedVersion,boolean succeeded,boolean retryable,String evidenceReference,String reasonCode,String reason,String actorId){}
