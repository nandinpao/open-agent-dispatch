package com.opensocket.aievent.database.persistence.handoff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.integration.handoff.AgentContextAccessEvent;
import com.opensocket.aievent.core.integration.handoff.AttachmentAvailabilityStatus;
import com.opensocket.aievent.core.integration.handoff.HandoffApprovalDecision;
import com.opensocket.aievent.core.integration.handoff.HandoffApprovalMode;
import com.opensocket.aievent.core.integration.handoff.HandoffAttachmentMetadata;
import com.opensocket.aievent.core.integration.handoff.HandoffContextApproval;
import com.opensocket.aievent.core.integration.handoff.HandoffContextField;
import com.opensocket.aievent.core.integration.handoff.HandoffContextPolicy;
import com.opensocket.aievent.core.integration.handoff.HandoffContextPolicyType;
import com.opensocket.aievent.core.integration.handoff.HandoffContextRequirement;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffFieldShareDecision;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotStatus;
import com.opensocket.aievent.core.integration.handoff.HandoffReconciliationClassification;
import com.opensocket.aievent.core.integration.handoff.HandoffReleaseEvidence;
import com.opensocket.aievent.core.integration.handoff.HandoffReleaseEvidenceType;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotReleaseStatus;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffReleaseEvidencePo;
import com.opensocket.aievent.core.integration.handoff.ResultContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.ResultSharingPolicyType;
import com.opensocket.aievent.core.organization.SensitivityLevel;
import com.opensocket.aievent.database.persistence.handoff.po.AgentContextAccessEventPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextApprovalPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextFieldDecisionPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextPolicyPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextSnapshotPo;
import com.opensocket.aievent.database.persistence.handoff.po.ResultContextSnapshotPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

import tools.jackson.databind.ObjectMapper;

@DatabasePersistenceConverter
public class HandoffContextPersistenceConverter {
    private final ObjectMapper json;

    public HandoffContextPersistenceConverter(ObjectMapper json) {
        this.json = json;
    }

    public HandoffContextPolicyPo toPo(HandoffContextPolicy value) {
        var po = new HandoffContextPolicyPo();
        po.setTenantId(value.tenantId());
        po.setPolicyId(value.policyId());
        po.setPolicyName(value.policyName());
        po.setPolicyType(value.policyType().name());
        po.setContextRequirement(value.contextRequirement().name());
        po.setDefaultFieldDecision(value.defaultFieldDecision().name());
        po.setAttachmentPolicy(value.attachmentPolicy().name());
        po.setApprovalMode(value.approvalMode().name());
        po.setAllowedFieldPathsJson(write(value.allowedFieldPaths()));
        po.setAllowedCommentTypesJson(write(value.allowedCommentTypes()));
        po.setMaskingRulesJson(write(value.maskingRules()));
        po.setResultSharingPolicy(value.resultSharingPolicy().name());
        po.setEnabled(value.enabled());
        po.setVersion(value.version());
        po.setCreatedAt(value.createdAt());
        po.setUpdatedAt(value.updatedAt());
        return po;
    }

    public HandoffContextPolicy policy(HandoffContextPolicyPo po) {
        return new HandoffContextPolicy(po.getTenantId(), po.getPolicyId(), po.getPolicyName(),
                HandoffContextPolicyType.valueOf(po.getPolicyType()),
                HandoffContextRequirement.valueOf(po.getContextRequirement()),
                HandoffFieldShareDecision.valueOf(po.getDefaultFieldDecision()),
                HandoffContextPolicyType.valueOf(po.getAttachmentPolicy()),
                HandoffApprovalMode.valueOf(po.getApprovalMode()),
                readList(po.getAllowedFieldPathsJson()), readList(po.getAllowedCommentTypesJson()),
                readStringMap(po.getMaskingRulesJson()),
                ResultSharingPolicyType.valueOf(po.getResultSharingPolicy()), po.isEnabled(), po.getVersion(),
                po.getCreatedAt(), po.getUpdatedAt());
    }

    public HandoffContextSnapshotPo toPo(HandoffContextSnapshot value) {
        var po = new HandoffContextSnapshotPo();
        po.setTenantId(value.tenantId());
        po.setSnapshotId(value.snapshotId());
        po.setAggregateId(value.aggregateId());
        po.setSchemaVersion(value.schemaVersion());
        po.setRootTaskId(value.rootTaskId());
        po.setSourceTaskId(value.sourceTaskId());
        po.setTargetTaskId(value.targetTaskId());
        po.setSourceAgentId(value.sourceAgentId());
        po.setTargetAgentId(value.targetAgentId());
        po.setTargetDomainId(value.targetDomainId());
        po.setTargetBindingHash(value.targetBindingHash());
        po.setContextPolicyId(value.contextPolicyId());
        po.setPolicyVersion(value.policyVersion());
        po.setSnapshotVersion(value.snapshotVersion());
        po.setSummary(value.summary());
        po.setStructuredContextJson(write(value.structuredContext()));
        po.setAllowedCommentRefsJson(write(value.allowedCommentRefs()));
        po.setAttachmentMetadataJson(write(value.attachmentMetadata()));
        po.setRedactedFieldPathsJson(write(value.redactedFieldPaths()));
        po.setOmittedContentReasonsJson(write(value.omittedContentReasons()));
        po.setSensitivityLevel(value.sensitivityLevel().name());
        po.setContentHash(value.contentHash());
        po.setSourceObservedAt(value.sourceObservedAt());
        po.setCreatedAt(value.createdAt());
        po.setCreatedByType(value.createdByType());
        po.setCreatedById(value.createdById());
        po.setExpiresAt(value.expiresAt());
        po.setStatus(value.status().name());
        po.setApprovedBy(value.approvedBy());
        po.setApprovedAt(value.approvedAt());
        po.setApprovalEvidenceHash(value.approvalEvidenceHash());
        po.setSupersedesSnapshotId(value.supersedesSnapshotId());
        po.setCorrelationId(value.correlationId());
        po.setReleaseStatus(value.releaseStatus().name());
        po.setReleaseEvidenceId(value.releaseEvidenceId());
        po.setReleasedAt(value.releasedAt());
        po.setLastReleaseErrorCode(value.lastReleaseErrorCode());
        po.setReconciliationClassification(value.reconciliationClassification().name());
        po.setNextReconcileAt(value.nextReconcileAt());
        po.setReconciliationCount(value.reconciliationCount());
        po.setRowVersion(value.rowVersion());
        return po;
    }

    public HandoffContextSnapshot snapshot(HandoffContextSnapshotPo po, List<HandoffContextFieldDecisionPo> rows) {
        return new HandoffContextSnapshot(po.getTenantId(), po.getSnapshotId(), po.getAggregateId(),
                po.getSchemaVersion(), po.getRootTaskId(), po.getSourceTaskId(), po.getTargetTaskId(),
                po.getSourceAgentId(), po.getTargetAgentId(), po.getTargetDomainId(), po.getTargetBindingHash(),
                po.getContextPolicyId(), po.getPolicyVersion(), po.getSnapshotVersion(), po.getSummary(),
                readObjectMap(po.getStructuredContextJson()), readList(po.getAllowedCommentRefsJson()),
                readAttachments(po.getAttachmentMetadataJson()), readList(po.getRedactedFieldPathsJson()),
                readList(po.getOmittedContentReasonsJson()), SensitivityLevel.valueOf(po.getSensitivityLevel()),
                po.getContentHash(), po.getSourceObservedAt(), po.getCreatedAt(), po.getCreatedByType(),
                po.getCreatedById(), po.getExpiresAt(), HandoffSnapshotStatus.valueOf(po.getStatus()),
                po.getApprovedBy(), po.getApprovedAt(), po.getApprovalEvidenceHash(), po.getSupersedesSnapshotId(),
                po.getCorrelationId(), rows.stream().map(this::field).toList(),
                HandoffSnapshotReleaseStatus.valueOf(po.getReleaseStatus()), po.getReleaseEvidenceId(),
                po.getReleasedAt(), po.getLastReleaseErrorCode(),
                HandoffReconciliationClassification.valueOf(po.getReconciliationClassification()),
                po.getNextReconcileAt(), po.getReconciliationCount(), po.getRowVersion());
    }

    public HandoffReleaseEvidencePo toPo(HandoffReleaseEvidence value) {
        var po = new HandoffReleaseEvidencePo();
        po.setTenantId(value.tenantId()); po.setEvidenceId(value.evidenceId()); po.setSnapshotId(value.snapshotId());
        po.setEvidenceType(value.evidenceType().name()); po.setReleaseStatus(value.releaseStatus().name());
        po.setClassification(value.classification().name()); po.setDispatchEvidenceReference(value.dispatchEvidenceReference());
        po.setReasonCode(value.reasonCode()); po.setAttemptNo(value.attemptNo()); po.setActorType(value.actorType());
        po.setActorId(value.actorId()); po.setCorrelationId(value.correlationId()); po.setOccurredAt(value.occurredAt());
        return po;
    }

    public HandoffReleaseEvidence releaseEvidence(HandoffReleaseEvidencePo po) {
        return new HandoffReleaseEvidence(po.getTenantId(), po.getEvidenceId(), po.getSnapshotId(),
                HandoffReleaseEvidenceType.valueOf(po.getEvidenceType()), HandoffSnapshotReleaseStatus.valueOf(po.getReleaseStatus()),
                HandoffReconciliationClassification.valueOf(po.getClassification()), po.getDispatchEvidenceReference(),
                po.getReasonCode(), po.getAttemptNo(), po.getActorType(), po.getActorId(), po.getCorrelationId(), po.getOccurredAt());
    }

    public HandoffContextFieldDecisionPo toPo(String tenantId, String snapshotId, HandoffContextField value) {
        var po = new HandoffContextFieldDecisionPo();
        po.setTenantId(tenantId);
        po.setSnapshotId(snapshotId);
        po.setFieldPath(value.fieldPath());
        po.setClassification(value.classification());
        po.setSensitivityLevel((value.sensitivityLevel() == null ? SensitivityLevel.INTERNAL : value.sensitivityLevel()).name());
        po.setShareDecision(value.shareDecision().name());
        po.setMaskingMethod(value.maskingMethod());
        po.setSourceReference(value.sourceReference());
        po.setProjectedValueJson(write(value.projectedValue()));
        po.setOriginalValueHash(value.originalValueHash());
        return po;
    }

    public HandoffContextField field(HandoffContextFieldDecisionPo po) {
        return new HandoffContextField(po.getFieldPath(), po.getClassification(),
                SensitivityLevel.valueOf(po.getSensitivityLevel()), HandoffFieldShareDecision.valueOf(po.getShareDecision()),
                po.getMaskingMethod(), po.getSourceReference(), readAny(po.getProjectedValueJson()), po.getOriginalValueHash());
    }

    public HandoffContextApprovalPo toPo(HandoffContextApproval value) {
        var po = new HandoffContextApprovalPo();
        po.setTenantId(value.tenantId()); po.setApprovalId(value.approvalId()); po.setSnapshotId(value.snapshotId());
        po.setDecision(value.decision().name()); po.setActorType(value.actorType()); po.setActorId(value.actorId());
        po.setReason(value.reason()); po.setIdempotencyKey(value.idempotencyKey()); po.setDecidedAt(value.decidedAt());
        po.setCorrelationId(value.correlationId());
        return po;
    }

    public HandoffContextApproval approval(HandoffContextApprovalPo po) {
        return new HandoffContextApproval(po.getTenantId(), po.getApprovalId(), po.getSnapshotId(),
                HandoffApprovalDecision.valueOf(po.getDecision()), po.getActorType(), po.getActorId(), po.getReason(),
                po.getIdempotencyKey(), po.getDecidedAt(), po.getCorrelationId());
    }

    public ResultContextSnapshotPo toPo(ResultContextSnapshot value) {
        var po = new ResultContextSnapshotPo();
        po.setTenantId(value.tenantId()); po.setResultSnapshotId(value.resultSnapshotId());
        po.setRootTaskId(value.rootTaskId()); po.setSourceTaskId(value.sourceTaskId());
        po.setTargetTaskId(value.targetTaskId()); po.setPolicyId(value.policyId());
        po.setSnapshotVersion(value.snapshotVersion()); po.setResultSummary(value.resultSummary());
        po.setSharedEvidenceRefsJson(write(value.sharedEvidenceRefs())); po.setMaskedOutputJson(write(value.maskedOutput()));
        po.setOmittedOutputReasonsJson(write(value.omittedOutputReasons())); po.setResultContentHash(value.resultContentHash());
        po.setCreatedByAgentId(value.createdByAgentId()); po.setCreatedAt(value.createdAt());
        po.setStatus(value.status()); po.setCorrelationId(value.correlationId());
        return po;
    }

    public ResultContextSnapshot result(ResultContextSnapshotPo po) {
        return new ResultContextSnapshot(po.getTenantId(), po.getResultSnapshotId(), po.getRootTaskId(),
                po.getSourceTaskId(), po.getTargetTaskId(), po.getPolicyId(), po.getSnapshotVersion(),
                po.getResultSummary(), readList(po.getSharedEvidenceRefsJson()), readObjectMap(po.getMaskedOutputJson()),
                readList(po.getOmittedOutputReasonsJson()), po.getResultContentHash(), po.getCreatedByAgentId(),
                po.getCreatedAt(), po.getStatus(), po.getCorrelationId());
    }

    public AgentContextAccessEventPo toPo(AgentContextAccessEvent value) {
        var po = new AgentContextAccessEventPo();
        po.setTenantId(value.tenantId()); po.setAccessEventId(value.accessEventId()); po.setTaskId(value.taskId());
        po.setAssignmentId(value.assignmentId()); po.setDispatchRequestId(value.dispatchRequestId());
        po.setAgentId(value.agentId()); po.setAgentSessionId(value.agentSessionId()); po.setSnapshotId(value.snapshotId());
        po.setSnapshotVersion(value.snapshotVersion()); po.setAccessDecision(value.accessDecision());
        po.setReasonCode(value.reasonCode()); po.setDispatchTokenHash(value.dispatchTokenHash());
        po.setClientAddress(value.clientAddress()); po.setCorrelationId(value.correlationId());
        po.setAccessedAt(value.accessedAt());
        return po;
    }

    public AgentContextAccessEvent access(AgentContextAccessEventPo po) {
        return new AgentContextAccessEvent(po.getTenantId(), po.getAccessEventId(), po.getTaskId(), po.getAssignmentId(),
                po.getDispatchRequestId(), po.getAgentId(), po.getAgentSessionId(), po.getSnapshotId(),
                po.getSnapshotVersion(), po.getAccessDecision(), po.getReasonCode(), po.getDispatchTokenHash(),
                po.getClientAddress(), po.getCorrelationId(), po.getAccessedAt());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private Object readAny(String value) {
        try { return value == null ? null : json.readValue(value, Object.class); } catch (Exception exception) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(String value) {
        try { return value == null ? Map.of() : json.readValue(value, Map.class); } catch (Exception exception) { return Map.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readStringMap(String value) {
        try { return value == null ? Map.of() : json.readValue(value, Map.class); } catch (Exception exception) { return Map.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<String> readList(String value) {
        try { return value == null ? List.of() : json.readValue(value, List.class); } catch (Exception exception) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<HandoffAttachmentMetadata> readAttachments(String value) {
        try {
            if (value == null) return List.of();
            List<Map<String, Object>> rows = json.readValue(value, List.class);
            var out = new ArrayList<HandoffAttachmentMetadata>();
            for (var row : rows) {
                String sourceReference = text(row.get("sourceReference"));
                if (sourceReference == null) sourceReference = text(row.get("sourceIssueKey"));
                out.add(new HandoffAttachmentMetadata(text(row.get("filename")), text(row.get("contentType")),
                        number(row.get("sizeBytes")), text(row.get("sha256")), sourceReference,
                        AttachmentAvailabilityStatus.valueOf(text(row.get("availabilityStatus")))));
            }
            return out;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
}
