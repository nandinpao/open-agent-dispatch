package com.opensocket.aievent.core.integration.handoff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.organization.SensitivityLevel;

/**
 * Immutable A2A Handoff aggregate version. External issue identifiers and raw credentials are
 * deliberately excluded. Operational release fields may advance through CAS; content fields do not.
 */
public record HandoffContextSnapshot(
        String tenantId,
        String snapshotId,
        String aggregateId,
        int schemaVersion,
        String rootTaskId,
        String sourceTaskId,
        String targetTaskId,
        String sourceAgentId,
        String targetAgentId,
        String targetDomainId,
        String targetBindingHash,
        String contextPolicyId,
        long policyVersion,
        int snapshotVersion,
        String summary,
        Map<String, Object> structuredContext,
        List<String> allowedCommentRefs,
        List<HandoffAttachmentMetadata> attachmentMetadata,
        List<String> redactedFieldPaths,
        List<String> omittedContentReasons,
        SensitivityLevel sensitivityLevel,
        String contentHash,
        OffsetDateTime sourceObservedAt,
        OffsetDateTime createdAt,
        String createdByType,
        String createdById,
        OffsetDateTime expiresAt,
        HandoffSnapshotStatus status,
        String approvedBy,
        OffsetDateTime approvedAt,
        String approvalEvidenceHash,
        String supersedesSnapshotId,
        String correlationId,
        List<HandoffContextField> fieldDecisions,
        HandoffSnapshotReleaseStatus releaseStatus,
        String releaseEvidenceId,
        OffsetDateTime releasedAt,
        String lastReleaseErrorCode,
        HandoffReconciliationClassification reconciliationClassification,
        OffsetDateTime nextReconcileAt,
        int reconciliationCount,
        long rowVersion) {

    public HandoffContextSnapshot {
        structuredContext = structuredContext == null ? Map.of() : Map.copyOf(structuredContext);
        allowedCommentRefs = allowedCommentRefs == null ? List.of() : List.copyOf(allowedCommentRefs);
        attachmentMetadata = attachmentMetadata == null ? List.of() : List.copyOf(attachmentMetadata);
        redactedFieldPaths = redactedFieldPaths == null ? List.of() : List.copyOf(redactedFieldPaths);
        omittedContentReasons = omittedContentReasons == null ? List.of() : List.copyOf(omittedContentReasons);
        fieldDecisions = fieldDecisions == null ? List.of() : List.copyOf(fieldDecisions);
        releaseStatus = releaseStatus == null
                ? (status == HandoffSnapshotStatus.APPROVED
                    ? HandoffSnapshotReleaseStatus.READY
                    : HandoffSnapshotReleaseStatus.WAITING_APPROVAL)
                : releaseStatus;
        reconciliationClassification = reconciliationClassification == null
                ? HandoffReconciliationClassification.NONE
                : reconciliationClassification;
        rowVersion = Math.max(1L, rowVersion);
    }
}
