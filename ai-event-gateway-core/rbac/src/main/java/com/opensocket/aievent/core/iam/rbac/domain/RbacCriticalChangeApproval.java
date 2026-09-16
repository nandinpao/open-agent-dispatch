package com.opensocket.aievent.core.iam.rbac.domain;

import java.time.Instant;
import java.util.Objects;

public final class RbacCriticalChangeApproval {
    private final String approvalId;
    private final String tenantId;
    private final RbacApprovalOperation operation;
    private final String requestHash;
    private final String requesterId;
    private final String targetType;
    private final String targetId;
    private final RbacApprovalStatus status;
    private final String approverId;
    private final String decisionReason;
    private final Instant requestedAt;
    private final Instant expiresAt;
    private final Instant decidedAt;
    private final Instant consumedAt;
    private final long version;

    private RbacCriticalChangeApproval(String approvalId, String tenantId, RbacApprovalOperation operation,
            String requestHash, String requesterId, String targetType, String targetId,
            RbacApprovalStatus status, String approverId, String decisionReason, Instant requestedAt,
            Instant expiresAt, Instant decidedAt, Instant consumedAt, long version) {
        this.approvalId = required(approvalId, "approvalId");
        this.tenantId = authorityScope(tenantId);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.requestHash = required(requestHash, "requestHash");
        this.requesterId = required(requesterId, "requesterId");
        this.targetType = required(targetType, "targetType");
        this.targetId = required(targetId, "targetId");
        this.status = Objects.requireNonNull(status, "status");
        this.approverId = approverId == null ? "" : approverId.trim();
        this.decisionReason = decisionReason == null ? "" : decisionReason.trim();
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must be after requestedAt");
        this.decidedAt = decidedAt;
        this.consumedAt = consumedAt;
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    public static RbacCriticalChangeApproval request(String approvalId, String tenantId,
            RbacApprovalOperation operation, String requestHash, String requesterId, String targetType,
            String targetId, Instant requestedAt, Instant expiresAt) {
        return new RbacCriticalChangeApproval(approvalId, tenantId, operation, requestHash, requesterId,
                targetType, targetId, RbacApprovalStatus.PENDING, "", "", requestedAt, expiresAt, null, null, 1);
    }

    public static RbacCriticalChangeApproval reconstitute(String approvalId, String tenantId,
            RbacApprovalOperation operation, String requestHash, String requesterId, String targetType,
            String targetId, RbacApprovalStatus status, String approverId, String decisionReason,
            Instant requestedAt, Instant expiresAt, Instant decidedAt, Instant consumedAt, long version) {
        return new RbacCriticalChangeApproval(approvalId, tenantId, operation, requestHash, requesterId,
                targetType, targetId, status, approverId, decisionReason, requestedAt, expiresAt, decidedAt, consumedAt, version);
    }

    public RbacCriticalChangeApproval approve(String actorId, String reason, Instant at) {
        requirePending(at);
        String actor = required(actorId, "actorId");
        if (requesterId.equals(actor)) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_SELF_APPROVAL_FORBIDDEN,
                    "Critical RBAC change must be approved by a different principal");
        }
        return new RbacCriticalChangeApproval(approvalId, tenantId, operation, requestHash, requesterId,
                targetType, targetId, RbacApprovalStatus.APPROVED, actor, required(reason, "reason"),
                requestedAt, expiresAt, at, null, version + 1);
    }

    public RbacCriticalChangeApproval reject(String actorId, String reason, Instant at) {
        requirePending(at);
        String actor = required(actorId, "actorId");
        if (requesterId.equals(actor)) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_SELF_APPROVAL_FORBIDDEN,
                    "Critical RBAC change must be rejected by a different principal");
        }
        return new RbacCriticalChangeApproval(approvalId, tenantId, operation, requestHash, requesterId,
                targetType, targetId, RbacApprovalStatus.REJECTED, actor, required(reason, "reason"),
                requestedAt, expiresAt, at, null, version + 1);
    }

    public RbacCriticalChangeApproval consume(String actorId, RbacApprovalOperation expectedOperation,
            String expectedHash, Instant at) {
        if (status != RbacApprovalStatus.APPROVED) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_REQUIRED,
                    "Critical RBAC change approval is not approved");
        }
        if (!expiresAt.isAfter(at)) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_EXPIRED,
                    "Critical RBAC change approval has expired");
        }
        if (!requesterId.equals(required(actorId, "actorId"))) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_REQUESTER_MISMATCH,
                    "Only the approval requester may execute the approved change");
        }
        if (operation != expectedOperation || !requestHash.equals(required(expectedHash, "expectedHash"))) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_PAYLOAD_MISMATCH,
                    "Approved RBAC payload does not match the requested mutation");
        }
        return new RbacCriticalChangeApproval(approvalId, tenantId, operation, requestHash, requesterId,
                targetType, targetId, RbacApprovalStatus.CONSUMED, approverId, decisionReason,
                requestedAt, expiresAt, decidedAt, at, version + 1);
    }

    private void requirePending(Instant at) {
        if (status != RbacApprovalStatus.PENDING) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_STATE_INVALID,
                    "Critical RBAC approval is not pending");
        }
        if (!expiresAt.isAfter(at)) {
            throw new RbacDomainException(RbacReasonCode.RBAC_CRITICAL_APPROVAL_EXPIRED,
                    "Critical RBAC approval has expired");
        }
    }

    public String approvalId() { return approvalId; }
    public String tenantId() { return tenantId; }
    public RbacApprovalOperation operation() { return operation; }
    public String requestHash() { return requestHash; }
    public String requesterId() { return requesterId; }
    public String targetType() { return targetType; }
    public String targetId() { return targetId; }
    public RbacApprovalStatus status() { return status; }
    public String approverId() { return approverId; }
    public String decisionReason() { return decisionReason; }
    public Instant requestedAt() { return requestedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant decidedAt() { return decidedAt; }
    public Instant consumedAt() { return consumedAt; }
    public long version() { return version; }

    private static String authorityScope(String value) {
        return value == null || value.isBlank() ? "INSTANCE" : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
