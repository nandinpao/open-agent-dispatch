package com.opensocket.aievent.core.a2a.core;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.opensocket.aievent.core.integration.handoff.HandoffContextReasonCode;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotStatus;

/** Pure release decision. No Task transition or Dispatch side effect is executed here. */
public final class HandoffDispatchReleaseGate {
    private final HandoffSnapshotIntegrityGuard integrity = new HandoffSnapshotIntegrityGuard();

    public Decision evaluate(HandoffContextSnapshot snapshot, CurrentBinding binding, OffsetDateTime now) {
        if (snapshot == null) return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_REQUIRED_BUT_UNAVAILABLE.name());
        if (!Objects.equals(snapshot.tenantId(), binding.tenantId())) {
            return Decision.blocked(HandoffContextReasonCode.CROSS_TENANT_HANDOFF_DENIED.name());
        }
        if (!Objects.equals(snapshot.sourceTaskId(), binding.sourceTaskId())) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_SOURCE_ASSIGNMENT_MISMATCH.name());
        }
        if (!Objects.equals(snapshot.targetTaskId(), binding.targetTaskId())) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH.name());
        }
        if (!Objects.equals(snapshot.targetDomainId(), binding.targetDomainId())) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH.name());
        }
        if (snapshot.targetAgentId() != null && !snapshot.targetAgentId().isBlank()
                && !"UNASSIGNED".equals(snapshot.targetAgentId())
                && !Objects.equals(snapshot.targetAgentId(), binding.targetAgentId())) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_AGENT_MISMATCH.name());
        }
        if (!Objects.equals(snapshot.sourceAgentId(), binding.sourceAgentId())) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_SOURCE_ASSIGNMENT_MISMATCH.name());
        }
        if (snapshot.policyVersion() != binding.policyVersion()) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_APPROVAL_BINDING_MISMATCH.name());
        }
        if (snapshot.status() != HandoffSnapshotStatus.APPROVED || snapshot.approvalEvidenceHash() == null
                || snapshot.approvalEvidenceHash().isBlank()) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_APPROVAL_REQUIRED.name());
        }
        if (snapshot.expiresAt() != null && !snapshot.expiresAt().isAfter(now)) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_SNAPSHOT_EXPIRED.name());
        }
        if (snapshot.schemaVersion() >= 2) {
            String currentBindingHash = integrity.targetBindingHash(binding.tenantId(), binding.targetTaskId(),
                    binding.targetAgentId(), binding.targetDomainId());
            if (!integrity.constantEquals(snapshot.targetBindingHash(), currentBindingHash)) {
                return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH.name());
            }
        }
        String actualHash = integrity.contentHash(snapshot.schemaVersion(), snapshot.tenantId(), snapshot.rootTaskId(),
                snapshot.sourceTaskId(), snapshot.targetTaskId(), snapshot.sourceAgentId(), snapshot.targetAgentId(),
                snapshot.targetDomainId(), snapshot.targetBindingHash(), snapshot.contextPolicyId(),
                snapshot.policyVersion(), snapshot.snapshotVersion(), snapshot.summary(), snapshot.structuredContext(),
                snapshot.allowedCommentRefs(), snapshot.attachmentMetadata(), snapshot.redactedFieldPaths(),
                snapshot.omittedContentReasons());
        if (!integrity.constantEquals(snapshot.contentHash(), actualHash)) {
            return Decision.blocked(HandoffContextReasonCode.HANDOFF_CONTEXT_CONTENT_HASH_CONFLICT.name());
        }
        return Decision.permitted();
    }

    public record CurrentBinding(String tenantId, String sourceTaskId, String targetTaskId,
                                 String sourceAgentId, String targetAgentId, String targetDomainId,
                                 long policyVersion) {
    }

    public record Decision(boolean allowed, String reasonCode) {
        public static Decision permitted() { return new Decision(true, null); }
        public static Decision blocked(String reasonCode) { return new Decision(false, reasonCode); }
    }
}
