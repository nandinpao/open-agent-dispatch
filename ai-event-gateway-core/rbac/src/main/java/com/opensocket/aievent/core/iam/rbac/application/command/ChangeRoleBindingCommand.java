package com.opensocket.aievent.core.iam.rbac.application.command;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;

/** Atomically changes the canonical responsibility carried by an existing principal-role binding. */
public record ChangeRoleBindingCommand(
        String tenantId,
        String bindingId,
        PrincipalRef expectedPrincipal,
        String roleId,
        String scopeType,
        String scopeId,
        String actorId,
        String auditReason,
        Instant occurredAt) { }
