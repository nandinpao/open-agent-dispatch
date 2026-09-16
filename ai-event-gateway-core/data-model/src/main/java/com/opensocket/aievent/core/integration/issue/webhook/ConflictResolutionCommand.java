package com.opensocket.aievent.core.integration.issue.webhook;
public record ConflictResolutionCommand(
 String tenantId,String conflictId,ConflictResolutionPolicy policy,String actorId,String reason,
 long expectedVersion,String idempotencyKey) {}
