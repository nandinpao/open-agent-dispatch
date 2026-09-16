package com.opensocket.aievent.core.iam.rbac.application.command;
import java.time.Instant;
public record ChangeRoleStatusCommand(String tenantId,String roleId,String status,String actorId,String auditReason,Instant occurredAt,long expectedVersion) { }
