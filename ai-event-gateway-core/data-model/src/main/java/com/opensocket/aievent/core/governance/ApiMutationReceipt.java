package com.opensocket.aievent.core.governance;
import java.time.OffsetDateTime;
public record ApiMutationReceipt(String tenantId,String receiptId,String requestMethod,String requestPath,String idempotencyKey,String requestHash,Long expectedVersion,String actorType,String actorId,String auditReason,String correlationId,String authorizationDecisionId,String permissionPoint,String status,String responseHash,String resultResourceType,String resultResourceId,Long resultVersion,String syncStatus,OffsetDateTime createdAt,OffsetDateTime completedAt) {}
