package com.opensocket.aievent.core.iam.rbac.application.command;
import java.time.Instant;
public record UpdateRoleCommand(String tenantId,String roleId,String roleName,String description,String actorId,String auditReason,Instant occurredAt,long expectedVersion) { }
