package com.opensocket.aievent.core.api.contract;
public record ApiMutationContext(String tenantId,String idempotencyKey,String correlationId,Long expectedVersion,String actorType,String actorId,String auditReason,String authorizationDecisionId,String permissionPoint) {}
