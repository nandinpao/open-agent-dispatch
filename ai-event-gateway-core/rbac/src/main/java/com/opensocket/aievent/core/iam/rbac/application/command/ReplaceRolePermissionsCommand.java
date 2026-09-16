package com.opensocket.aievent.core.iam.rbac.application.command;
import java.time.Instant;import java.util.Set;
public record ReplaceRolePermissionsCommand(String tenantId,String roleId,Set<String> permissionCodes,String actorId,String auditReason,Instant occurredAt,long expectedRoleVersion) { }
