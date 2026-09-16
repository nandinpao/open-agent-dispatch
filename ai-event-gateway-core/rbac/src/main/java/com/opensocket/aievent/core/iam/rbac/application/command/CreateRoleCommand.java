package com.opensocket.aievent.core.iam.rbac.application.command;
import java.time.Instant;
public record CreateRoleCommand(String roleId,String tenantId,boolean platformRole,String roleCode,String roleName,String description,String actorId,String auditReason,Instant occurredAt) { }
