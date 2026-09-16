package com.opensocket.aievent.core.iam.rbac.application.command;
import java.time.Instant;
public record RevokeRoleBindingCommand(String tenantId,String bindingId,String actorId,String reason,Instant occurredAt,long expectedVersion) { }
