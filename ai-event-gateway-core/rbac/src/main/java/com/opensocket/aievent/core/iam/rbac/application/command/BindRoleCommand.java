package com.opensocket.aievent.core.iam.rbac.application.command;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
public record BindRoleCommand(String bindingId,String tenantId,PrincipalRef principal,String roleId,String scopeType,String scopeId,Instant effectiveAt,Instant expiresAt,String actorId,String auditReason,Instant occurredAt) { }
