package com.opensocket.aievent.core.integration.handoff;
import java.time.OffsetDateTime;
public record HandoffContextApproval(String tenantId,String approvalId,String snapshotId,HandoffApprovalDecision decision,String actorType,String actorId,String reason,String idempotencyKey,OffsetDateTime decidedAt,String correlationId) {}
