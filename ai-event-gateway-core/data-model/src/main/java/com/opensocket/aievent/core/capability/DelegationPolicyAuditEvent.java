package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Append-only Phase 3 policy mutation evidence. */
public record DelegationPolicyAuditEvent(
        String tenantId,
        String eventId,
        String policyId,
        Integer policyVersion,
        String action,
        String reason,
        String actorRef,
        OffsetDateTime occurredAt) {}
