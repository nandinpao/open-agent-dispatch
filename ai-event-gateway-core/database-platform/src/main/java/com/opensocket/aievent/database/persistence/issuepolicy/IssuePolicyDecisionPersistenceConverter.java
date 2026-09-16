package com.opensocket.aievent.database.persistence.issuepolicy;

import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyAutomationStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyBindingStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionOutcome;
import com.opensocket.aievent.database.persistence.issuepolicy.po.IssuePolicyDecisionPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
public class IssuePolicyDecisionPersistenceConverter {
    public IssuePolicyDecisionPo toPo(IssuePolicyDecision v) {
        IssuePolicyDecisionPo p = new IssuePolicyDecisionPo();
        p.setTenantId(v.tenantId()); p.setDecisionId(v.decisionId()); p.setTaskId(v.taskId());
        p.setProjectionPurpose(v.projectionPurpose()); p.setPolicyId(v.policyId()); p.setPolicyVersion(v.policyVersion());
        p.setTaskIssueSyncPolicy(v.taskIssueSyncPolicy()); p.setDecision(v.decision().name()); p.setReasonCode(v.reasonCode());
        p.setSourceEventId(v.sourceEventId()); p.setSourceEventType(v.sourceEventType()); p.setTaskStatus(v.taskStatus());
        p.setBindingStatus(v.bindingStatus().name()); p.setConnectionId(v.connectionId()); p.setProjectMappingId(v.projectMappingId());
        p.setProjectMappingVersion(v.projectMappingVersion()); p.setProjectMappingSchemaHash(v.projectMappingSchemaHash());
        p.setProjectionId(v.projectionId()); p.setTaskIssueLinkId(v.taskIssueLinkId()); p.setOutboxId(v.outboxId());
        p.setIssueOperation(v.issueOperation()); p.setAdapterActionId(v.adapterActionId());
        p.setActionIdempotencyKey(v.actionIdempotencyKey()); p.setTerminalGeneration(v.terminalGeneration());
        p.setAutomationStatus(v.automationStatus().name()); p.setLastErrorCode(v.lastErrorCode()); p.setLastErrorMessage(v.lastErrorMessage());
        p.setCorrelationId(v.correlationId()); p.setCausationId(v.causationId()); p.setTraceId(v.traceId());
        p.setActorType(v.actorType()); p.setActorId(v.actorId()); p.setVersion(v.version());
        p.setCreatedAt(v.createdAt()); p.setUpdatedAt(v.updatedAt());
        return p;
    }

    public IssuePolicyDecision toDomain(IssuePolicyDecisionPo p) {
        return new IssuePolicyDecision(
                p.getTenantId(), p.getDecisionId(), p.getTaskId(), p.getProjectionPurpose(), p.getPolicyId(), p.getPolicyVersion(),
                p.getTaskIssueSyncPolicy(), IssuePolicyDecisionOutcome.valueOf(p.getDecision()), p.getReasonCode(), p.getSourceEventId(),
                p.getSourceEventType(), p.getTaskStatus(), IssuePolicyBindingStatus.valueOf(p.getBindingStatus()), p.getConnectionId(),
                p.getProjectMappingId(), p.getProjectMappingVersion(), p.getProjectMappingSchemaHash(), p.getProjectionId(),
                p.getTaskIssueLinkId(), p.getOutboxId(), p.getIssueOperation(), p.getAdapterActionId(), p.getActionIdempotencyKey(),
                p.getTerminalGeneration(), IssuePolicyAutomationStatus.valueOf(p.getAutomationStatus()), p.getLastErrorCode(),
                p.getLastErrorMessage(), p.getCorrelationId(), p.getCausationId(), p.getTraceId(), p.getActorType(), p.getActorId(),
                p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
